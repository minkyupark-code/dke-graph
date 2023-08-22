package com.etri.sodasapi.objectstorage.rgw;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.etri.sodasapi.objectstorage.common.*;
import com.etri.sodasapi.objectstorage.common.SQuota;
import com.etri.sodasapi.config.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.twonote.rgwadmin4j.RgwAdmin;
import org.twonote.rgwadmin4j.RgwAdminBuilder;
import org.twonote.rgwadmin4j.model.*;
import software.amazon.awssdk.core.exception.SdkClientException;

import javax.swing.plaf.ScrollBarUI;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RGWService {
    private final Constants constants;
    private RgwAdmin rgwAdmin;
    private SodasRgwAdmin sodasRgwAdmin;

    private synchronized RgwAdmin getRgwAdmin() {
        if (this.rgwAdmin == null) {
            rgwAdmin = new RgwAdminBuilder().accessKey(constants.getRgwAdminAccess())
                    .secretKey(constants.getRgwAdminSecret())
                    .endpoint(constants.getRgwEndpoint() + "/admin")
                    .build();
        }
        return rgwAdmin;
    }

    private SodasRgwAdmin getSodasRgwAdmin(){
        if(this.sodasRgwAdmin == null){
            sodasRgwAdmin = new SodasRgwAdmin(constants);
        }
        return sodasRgwAdmin;
    }


    public List<SBucket> getBuckets(Key key) {
        AmazonS3 conn = getClient(key);
        List<Bucket> buckets = conn.listBuckets();
        List<SBucket> bucketList = new ArrayList<>();

        for (Bucket mybucket : buckets) {
            bucketList.add(new SBucket(mybucket.getName(), mybucket.getCreationDate()));
        }
        return bucketList;
    }

    public List<BObject> getObjects(Key key, String bucketName) {
        AmazonS3 conn = getClient(key);
        ObjectListing objects = conn.listObjects(bucketName);

        List<BObject> objectList = new ArrayList<>();

        do {
            for (S3ObjectSummary objectSummary : objects.getObjectSummaries()) {
                objectList.add(new BObject(objectSummary.getKey(), objectSummary.getSize(), objectSummary.getLastModified()));
//                System.out.println(objectSummary.getKey() + " " + conn.getObjectAcl(bucketName, objectSummary.getKey()));
            }
            objects = conn.listNextBatchOfObjects(objects);
        } while (objects.isTruncated());
        return objectList;
    }

    public Bucket createBucket(Key key, String bucketName) {
        AmazonS3 conn = getClient(key);
        return conn.createBucket(bucketName);
    }

    public void deleteBucket(Key key, String bucketName) {
        AmazonS3 conn = getClient(key);

        List<BObject> objectList = getObjects(key, bucketName);

        for (BObject bObject : objectList) {
            conn.deleteObject(bucketName, bObject.getObjectName());
        }

        conn.deleteBucket(bucketName);
    }

    public void deleteObject(Key key, String bucketName, String object) {
        AmazonS3 conn = getClient(key);

        conn.deleteObject(bucketName, object);
    }

    private synchronized AmazonS3 getClient(Key key) {
        AmazonS3 amazonS3;

        String accessKey = key.getAccessKey();
        String secretKey = key.getSecretKey();

        AWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        return amazonS3 = AmazonS3ClientBuilder
                .standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(constants.getRgwEndpoint(), Regions.DEFAULT_REGION.getName()))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfiguration)
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .build();
    }

    public void objectUpload(MultipartFile file, String bucketName, Key key) throws IOException {
        AmazonS3 conn = getClient(key);
//        ByteArrayInputStream input = new ByteArrayInputStream(file.getBytes());
//        byte[] bytes = input.readAllBytes();
        long partSize = 30 * 1024 * 1024;
        long contentLength = file.getSize();
        int partCount = (int) Math.ceil((double) contentLength / partSize);

        List<PartETag> partETags = new ArrayList<>();

        String uploadId = initiateMultipartUpload(bucketName,file.getOriginalFilename(), conn);

        for (int i = 0; i < partCount; i++) {
            long start = i * partSize;
            long end = Math.min(start + partSize, contentLength);

            try (InputStream inputStream = file.getInputStream()) {
                long skipBytes = inputStream.skip(start);
                if (skipBytes != start) {
                    throw new IOException("Could not skip to the desired position.");
                }

                byte[] buffer = new byte[(int) (end - start)];
                int bytesRead = inputStream.read(buffer);

                UploadPartRequest uploadPartRequest = new UploadPartRequest()
                        .withBucketName(bucketName)
                        .withKey(file.getOriginalFilename())
                        .withUploadId(uploadId)
                        .withPartNumber(i + 1)
                        .withInputStream(new ByteArrayInputStream(buffer))
                        .withPartSize(bytesRead);

                UploadPartResult uploadPartResult = conn.uploadPart(uploadPartRequest);
                partETags.add(uploadPartResult.getPartETag());
            }
        }

        CompleteMultipartUploadRequest completeRequest = new CompleteMultipartUploadRequest()
                .withBucketName(bucketName)
                .withKey(file.getOriginalFilename())
                .withUploadId(uploadId)
                .withPartETags(partETags);

        conn.completeMultipartUpload(completeRequest);

        //        PutObjectRequest request = new PutObjectRequest(bucketName, file.getOriginalFilename(), file.getInputStream(), null)
//        System.out.println(conn.putObject(bucketName, file.getOriginalFilename(), bytes, new ObjectMetadata()));
//        System.out.println(conn.putObject(request));
    }

    public String initiateMultipartUpload(String bucketName, String key, AmazonS3 conn){
        InitiateMultipartUploadRequest initiateRequest = new InitiateMultipartUploadRequest(bucketName, key);
        InitiateMultipartUploadResult initiateResult = conn.initiateMultipartUpload(initiateRequest);
        return initiateResult.getUploadId();
    }

    // TODO: 2023.7.22 Keycloak과 연동해 관리자 확인하는 코드 추가해야 함.
    public boolean validAccess(Key key) {
        return true;
    }

    public URL objectDownUrl(Key key, String bucketName, String object) {
        AmazonS3 conn = getClient(key);

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, object);
        ;
        return conn.generatePresignedUrl(request);
    }

    public Map<String, Long> getIndividualBucketQuota(String bucketName) {
        RgwAdmin rgwAdmin = getRgwAdmin();

        Optional<BucketInfo> bucketInfo = rgwAdmin.getBucketInfo(bucketName);
        BucketInfo bucketInfo1 = bucketInfo.get();

        Map<String, Long> individualBucketQuota = new HashMap<>();

        individualBucketQuota.put("max-size-kb", bucketInfo1.getBucketQuota().getMaxSizeKb());
        individualBucketQuota.put("max-objects", bucketInfo1.getBucketQuota().getMaxObjects());
        individualBucketQuota.put("actual-size", bucketInfo1.getUsage().getRgwMain().getSize_actual());

        return individualBucketQuota;
    }

    public void getBucketInfo(String bucketName){
        RgwAdmin rgwAdmin = getRgwAdmin();

        long usage =  rgwAdmin.getBucketInfo(bucketName).get().getUsage().getRgwMain().getSize();

        System.out.println(usage);
    }


    public SQuota setIndividualBucketQuota(String uid, String bucketName, SQuota quota){
        RgwAdmin rgwAdmin = getRgwAdmin();

        if(rgwAdmin.getUserQuota(uid).get().getMaxSizeKb() >= Long.parseLong(quota.getMax_size_kb())
            && rgwAdmin.getUserQuota(uid).get().getMaxObjects() >= Long.parseLong(quota.getMax_objects())){
            rgwAdmin.setIndividualBucketQuota(uid, bucketName, Long.parseLong(quota.getMax_objects()), Long.parseLong(quota.getMax_size_kb()));
        }

        return quota;
    }

    public Double quotaUtilizationInfo(String bucketName) {
        RgwAdmin rgwAdmin = getRgwAdmin();

        Optional<BucketInfo> bucketInfo = rgwAdmin.getBucketInfo(bucketName);
        BucketInfo bucketInfo1 = bucketInfo.get();
        return (((double) bucketInfo1.getUsage().getRgwMain().getSize_actual() / (bucketInfo1.getBucketQuota().getMaxSizeKb() * 1024)) * 100);
    }

    public Map<String, Double> quotaUtilizationList(Key key){
        List<SBucket> bucketList = getBuckets(key);

        Map<String, Double> quotaUtilizationMap = new HashMap<>();

        for(SBucket sBucket : bucketList){
            quotaUtilizationMap.put(sBucket.getBucketName(), quotaUtilizationInfo(sBucket.getBucketName()));
        }

        return quotaUtilizationMap;
    }

    public List<SubUser> createSubUser(String uid, SSubUser subUser) {
        RgwAdmin rgwAdmin = getRgwAdmin();
        Map<String, String> subUserParam = new HashMap<>();
        subUserParam.put("access-key", subUser.getAccessKey());
        subUserParam.put("secret-key", subUser.getSecretKey());
        subUserParam.put("key-type", "s3");
        subUserParam.put("access", SubUser.Permission.NONE.toString());
        return rgwAdmin.createSubUser(uid, subUser.getSubUid(), subUserParam);
    }

    public String subUserInfo(String uid, String subUid) {
        RgwAdmin rgwAdmin = getRgwAdmin();
        Optional<SubUser> optionalSubUser = rgwAdmin.getSubUserInfo(uid, subUid);

        SubUser subUser = optionalSubUser.get();

        return subUser.getPermission().toString();
    }

    public void setSubUserPermission(String uid, String subUid, String permission) {
        RgwAdmin rgwAdmin = getRgwAdmin();

        rgwAdmin.setSubUserPermission(uid, subUid, SubUser.Permission.valueOf(permission.toUpperCase()));

    }

    public Map<String, String> subUserList(String uid){
        RgwAdmin rgwAdmin = getRgwAdmin();

        List<String> subUserList = rgwAdmin.listSubUser(uid);

        Map<String, String> userInfoMap = new HashMap<>();

        for(String subUser : subUserList){
            userInfoMap.put(subUser, subUserInfo(uid, subUser).toUpperCase());
        }

        return userInfoMap;
    }

    public void deleteSubUser(String uid, String subUid, Key key) {
        RgwAdmin rgwAdmin = getRgwAdmin();
        rgwAdmin.removeS3CredentialFromSubUser(uid, subUid, key.getAccessKey());
        rgwAdmin.removeSubUser(uid, subUid);
    }

    public void alterSubUserKey(String uid, String subUid, Key key) {
        RgwAdmin rgwAdmin = getRgwAdmin();
        rgwAdmin.removeS3CredentialFromSubUser(uid, subUid, key.getAccessKey());
        rgwAdmin.createS3CredentialForSubUser(uid, subUid, key.getAccessKey(), key.getSecretKey());
    }

    // nodejs 코드에서 입력 파라미터로 uid만을 받게 설계돼 있어서 우리도 key 빼야할지 고민해봐야함.
    public void createS3Credential(String uid, Key key){
        RgwAdmin rgwAdmin = getRgwAdmin();

        rgwAdmin.createS3Credential(uid, key.getAccessKey(), key.getSecretKey());
    }

    public List<S3Credential> createS3Credential(String uid){
        RgwAdmin rgwAdmin = getRgwAdmin();

        return rgwAdmin.createS3Credential(uid);
    }

    public void deleteS3Credential(String uid, String accessKey){
        RgwAdmin rgwAdmin = getRgwAdmin();
        rgwAdmin.removeS3Credential(uid, accessKey);
    }

    public List<S3Credential> getS3Credential(String uid){
        RgwAdmin rgwAdmin = getRgwAdmin();
        Optional<User> userInfo = rgwAdmin.getUserInfo(uid);

        return userInfo.map(User::getS3Credentials).orElse(null);
    }

    public String getUserRatelimit(String uid){
        SodasRgwAdmin sodasRgwAdmin = getSodasRgwAdmin();
        
        return sodasRgwAdmin.getUserRateLimit(uid);
    }

    public Map<String, Map<String, Quota>> usersQuota(){
        RgwAdmin rgwAdmin = getRgwAdmin();

        List<User> userList = rgwAdmin.listUserInfo();

        Map<String, Map<String, Quota>> userInfo = new HashMap<>();
        Map<String, Quota> quota;

        for(User user : userList){
            quota = new HashMap<>();
            Quota userQuota = rgwAdmin.getUserQuota(user.getUserId()).get();
            quota.put("userQuota", userQuota);

//            String userRateLimit = getUserRatelimit(user.getUserId());
//            quotaAndRateLimit.put("RateLimit", userRateLimit);

            userInfo.put(user.getUserId(), quota);
        }

        return userInfo;
    }

    public Map<String, Map<String, String>> usersRateLimit(){
        RgwAdmin rgwAdmin = getRgwAdmin();

        List<User> userList = rgwAdmin.listUserInfo();

        Map<String, Map<String, String>> userRateInfo = new HashMap<>();
        Map<String, String> rateLimit;

        for(User user : userList){
            rateLimit = new HashMap<>();

            String userRateLimit = getUserRatelimit(user.getUserId());
            rateLimit.put("RateLimit", userRateLimit);

            userRateInfo.put(user.getUserId(), rateLimit);
        }

        return userRateInfo;
    }

    public Map<String, Map<String, Quota>> bucketsQuota(){
        RgwAdmin rgwAdmin = getRgwAdmin();

        List<User> userList = rgwAdmin.listUserInfo();

        Map<String, Map<String, Quota>> bucketInfo = new HashMap<>();
        Map<String, Quota> quota;

        for(User user : userList){
            quota = new HashMap<>();
            Quota bucketQuota = rgwAdmin.getBucketQuota(user.getUserId()).get();
            quota.put("bucketQuota", bucketQuota);

//            String userRateLimit = getUserRatelimit(user.getUserId());
//            quotaAndRateLimit.put("RateLimit", userRateLimit);

            bucketInfo.put(user.getUserId(), quota);
        }

        return bucketInfo;
    }


    public String setUserRateLimit(String uid, RateLimit rateLimit){
        SodasRgwAdmin sodasRgwAdmin = getSodasRgwAdmin();


        return sodasRgwAdmin.setUserRateLimit(uid, rateLimit);
    }


    public Map<String, List<?>> getFileList(Key key, String bucketName, String prefix) {

        String actualPrefix = (prefix != null) ? prefix : "";
        final AmazonS3 s3 = getClient(key);

        try {
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                    .withBucketName(bucketName)
                    .withDelimiter("/")
                    .withPrefix(actualPrefix);

            ObjectListing objectListing = s3.listObjects(listObjectsRequest);

            // 현재 디렉토리의 폴더만 가져옴
            List<String> folderList = objectListing.getCommonPrefixes()
                    .stream()
                    .filter(commonPrefix -> commonPrefix.startsWith(actualPrefix))
                    .collect(Collectors.toList());

            // 현재 디렉토리의 파일만 가져옴
            List<S3ObjectSummary> fileList = objectListing.getObjectSummaries()
                    .stream()
                    .filter(objectSummary -> objectSummary.getKey().startsWith(actualPrefix) && !folderList.contains(objectSummary.getKey() + "/"))
                    .collect(Collectors.toList());

            Map<String, List<?>> result = new HashMap<>();
            result.put("folders", folderList);
            result.put("files", fileList);

            return result;

        } catch (AmazonS3Exception | SdkClientException e) {
            e.printStackTrace();
        }

        return null;
    }

    public User createUser(SUser user) {
        RgwAdmin rgwAdmin = getRgwAdmin();
        Map<String, String> userParameters = new HashMap<>();
        userParameters.put("display-name", user.getDisplayName());
        userParameters.put("email", user.getEmail());
        return rgwAdmin.createUser(user.getUid(), userParameters);
    }

    public void addBucketUser(Key key, String rgwuser, String permission, String bucketName) {
        AmazonS3 conn = getClient(key);

        AccessControlList accessControlList = conn.getBucketAcl(bucketName);
        Grant grant = new Grant(new CanonicalGrantee(rgwuser), Permission.valueOf(permission));

        accessControlList.grantAllPermissions(grant);
        conn.setBucketAcl(bucketName, accessControlList);

        List<BObject> objectList = getObjects(key, bucketName);

        addObjectPermission(conn, objectList, grant.getPermission(), bucketName);
    }

    public void addObjectPermission(AmazonS3 conn, List<BObject> objectList, Permission permission, String bucketName){
        for(BObject bObject : objectList){
            Grant grant = new Grant(new CanonicalGrantee(conn.getS3AccountOwner().getId()), permission);
            AccessControlList accessControlList = conn.getObjectAcl(bucketName, bObject.getObjectName());
            accessControlList.grantAllPermissions(grant);
            conn.setObjectAcl(bucketName, bObject.getObjectName(), accessControlList);
        }
    }





    public void bucketAclTest() {
        AmazonS3 conn = getClient(new Key("MB9VKP4AC9TZPV1UDEO4" , "UYScnoXxLtmAemx4gAPjByZmbDnaYuOPOdpG7vMw"));
//        AccessControlList accessControlList = conn.getBucketAcl("foo-test-bucket2");
        // 기존 Grant를 가져올 Canonical ID 또는 AWS 계정 ID
//        String existingCanonicalId = "foo_user2";
//
//        AccessControlList accessControlList = conn.getBucketAcl("foo-test-bucket2");
//        Grant grant4 = new Grant(new CanonicalGrantee("foo_user2"), Permission.FullControl);
//
//        accessControlList.grantAllPermissions(grant4);
//        conn.setBucketAcl("foo-test-bucket2", accessControlList);
//
//        List<BObject> objectList = getObjects(new Key("MB9VKP4AC9TZPV1UDEO4" , "UYScnoXxLtmAemx4gAPjByZmbDnaYuOPOdpG7vMw"), "foo-test-bucket2");
//
//        System.out.println(conn.getBucketAcl("foo-test-bucket2"));
//
//
//        for(BObject bObject : objectList){
//            AccessControlList accessControlList12 = conn.getObjectAcl("foo-test-bucket2", bObject.getObjectName());
//            Grant grant5 = new Grant(new CanonicalGrantee("foo_user2"), Permission.FullControl);
//
//            accessControlList12.grantAllPermissions(grant5);
//
//            conn.setObjectAcl("foo-test-bucket2",bObject.getObjectName(), accessControlList12);
//        }
        conn.setBucketAcl("foo-test-bucket2", CannedAccessControlList.PublicRead);
    }
}
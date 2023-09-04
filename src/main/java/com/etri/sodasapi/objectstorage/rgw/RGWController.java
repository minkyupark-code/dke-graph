package com.etri.sodasapi.objectstorage.rgw;

import com.amazonaws.services.s3.model.Bucket;
import com.etri.sodasapi.auth.GetIdFromToken;
import com.etri.sodasapi.auth.KeycloakAdapter;
import com.etri.sodasapi.objectstorage.common.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.twonote.rgwadmin4j.model.Quota;
import org.twonote.rgwadmin4j.model.S3Credential;
import org.twonote.rgwadmin4j.model.SubUser;
import org.twonote.rgwadmin4j.model.User;

import java.io.IOException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Tag(name = "RGW Controller", description = "RGW 컨트롤러 API 문서입니다")
@RestController
@RequiredArgsConstructor
@RequestMapping("/object-storage")
public class RGWController {
    private final RGWService rgwService;
    private final String PF_ADMIN = "/organization/default_org/roles/platform_admin";

    /*
        Permission - Data - List
        버킷 정보를 읽어옴
     */
    @Operation(summary = "bucket 조회", description = "key 값을 읽어 해당 key값의 bucket을 조회합니다", responses = {
            @ApiResponse(responseCode = "200", description = "bucket 조회 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SBucket.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @GetMapping("/bucket")
    public ResponseEntity<List<SBucket>> getBuckets(@Parameter(name = "key", description = "해당 key 값") Key key, @GetIdFromToken Map<String, Object> userInfo) {
        return ResponseEntity.status(HttpStatus.OK).body(rgwService.getBuckets(key));
    }

    /*
        Permission - Data - Create
     */
    @Operation(summary = "bucket 생성", description = "key 값과 버킷 이름을 입력하여 bucket을 생성합니다", responses = {
            @ApiResponse(responseCode = "200", description = "bucket 생성 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SBucket.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @PostMapping("/bucket/{bucketName}")
    public ResponseEntity<Bucket> createBucket(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "해당 key 값") @RequestBody Key key,
                                               @Parameter(name = "bucketName", description = "버킷 이름") @PathVariable String bucketName) {
        return ResponseEntity.status(HttpStatus.OK).body(rgwService.createBucket(key, bucketName));
    }

    /*
        Permission - Data - Delete
     */
    @Operation(summary = "bucket 삭제", description = "key값을 확인하여 해당 bucket을 삭제합니다", responses = {
            @ApiResponse(responseCode = "200", description = "bucket 삭제 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @PostMapping("/bucket/{bucketName}")
    public void deleteBucket(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "해당 key 값")@RequestBody Key key,
                             @Parameter(name = "bucketName", description = "버킷 이름") @PathVariable String bucketName) {
        rgwService.deleteBucket(key, bucketName);
    }

    /*
        Data - List
     */
    @Operation(summary = "Object 조회", description = "key 값과 버킷 이름을 확인하여 해당 Objects를 조회합니다", responses = {
            @ApiResponse(responseCode = "200", description = "Object 조회 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BObject.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @GetMapping("/data/{bucketName}")
    public ResponseEntity<List<BObject>> getObjects(@Parameter(name = "bucketName", description = "버킷 이름")@PathVariable String bucketName,
                                                    Key key
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(rgwService.getObjects(key, bucketName));
    }

    /*
        Data - Delete
     */
    @Operation(summary = "Object 삭제", description = "key값과 버킷 이름을 확인하여 해당 Object를 삭제합니다", responses = {
            @ApiResponse(responseCode = "200", description = "Object 삭제 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @PostMapping("/data/{bucketName}/{object}")
    public void deleteObject(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "해당 key 값") @RequestBody Key key,
                             @Parameter(name = "bucketName", description = "버킷 이름") @PathVariable String bucketName,
                             @Parameter(name = "object", description = "해당 object") @PathVariable String object) {
        rgwService.deleteObject(key, bucketName, object);
    }

    /*
        Data - Create
     */
    @Operation(summary = "object 생성", description = "파일,버킷 이름,접근키,비밀키를 입력하여 오브젝트를 생성합니다", responses = {
            @ApiResponse(responseCode = "200", description = "Object 생성 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BObject.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @PostMapping("/data")
    public String objectUpload(@Parameter(name = "file", description = "파일") @RequestPart(value = "file", required = false) MultipartFile file,
                               @Parameter(name = "bucketName", description = "버킷 이름") @RequestParam("bucketName") String bucketName,
                               @Parameter(name = "accessKey", description = "접근키") @RequestParam("accessKey") String accessKey,
                               @Parameter(name = "secretKey", description = "비밀키") @RequestParam("secretKey") String secretKey) throws IOException {
        Key key = new Key(accessKey, secretKey);

        rgwService.objectUpload(file, bucketName, key);

        return file.getOriginalFilename();
    }

    /*
        Data - Get
     */
    @Operation(summary = "object의 url 다운로드", description = "key 값과 버킷 이름, 오브젝트를 입력하여 해당 오브젝트의 url을 다운로드합니다", responses = {
            @ApiResponse(responseCode = "200", description = "Object의 url 다운로드 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @GetMapping("/data/{bucketName}/{object}")
    public URL objectDownUrl(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "해당 key 값")@RequestBody Key key,
                             @Parameter(name = "bucketName", description = "버킷 이름") @PathVariable String bucketName,
                             @Parameter(name = "object", description = "오브젝트") @PathVariable String object) {
        return rgwService.objectDownUrl(key, bucketName, object);
    }

    // TODO: excel 정리된 것 처럼 매핑해야함
    @Operation(summary = "버킷 사용자 추가", description = "key 값과 user, bucketName, permission을 입력하여 사용자에게 해당 버킷의 권한을 부여합니다")
    @PostMapping("/permission/acl/bucket/{bucketName}")
    public void addBucketUser(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "해당 key 값") @RequestBody Key key,
                              @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "유저") @RequestBody String rgwUser,
                              @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "권한")@RequestBody String permission,
                              @Parameter(name = "bucketName", description = "버킷 이름") @PathVariable String bucketName) {
        rgwService.addBucketUser(key, rgwUser, permission, bucketName);
    }

    @Operation(summary = "테스트용 api")
    @GetMapping("/bucket/test")
    public void test(@GetIdFromToken Map<String, Object> userInfo) {
        Key key = new Key("MB9VKP4AC9TZPV1UDEO4", "UYScnoXxLtmAemx4gAPjByZmbDnaYuOPOdpG7vMw");
        String bucketName = "foo-test-bucket";
        String prefix = "test";
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJoV0NadE12M2s0eUJ2T012Ml85Y21SbTBHN0Zuc094czNGVkpuZGc3NmI4In0.eyJleHAiOjE2OTI1OTgzOTQsImlhdCI6MTY5MjU5NzQ5NCwianRpIjoiNjk3ZTMxZTUtOTU2YS00Y2ZlLWE0NjQtMGU0NTI0NDcxY2VhIiwiaXNzIjoiaHR0cDovL2tleWNsb2FrLjIyMS4xNTQuMTM0LjMxLnRyYWVmaWsubWU6MTAwMTcvcmVhbG1zL21hc3Rlci1pIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6IjI2MzMxOTEzLTJkOGEtNDFkNS05NzFhLWE0NzAwMzAyOTIxYyIsInR5cCI6IkJlYXJlciIsImF6cCI6InBsYXRmb3JtIiwic2Vzc2lvbl9zdGF0ZSI6IjRlYzhjNzU4LThhZmYtNDM4OS1iNGQwLWZkMzEyMDhjYzVlNCIsImFsbG93ZWQtb3JpZ2lucyI6WyIvKiJdLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsib2ZmbGluZV9hY2Nlc3MiLCJ1bWFfYXV0aG9yaXphdGlvbiIsInBsYXRmb3JtX2FkbWluIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJwcm9maWxlIGVtYWlsIiwic2lkIjoiNGVjOGM3NTgtOGFmZi00Mzg5LWI0ZDAtZmQzMTIwOGNjNWU0IiwiZ3JvdXBfbWVtYmVyc2hpcCI6WyJvZmZsaW5lX2FjY2VzcyIsInVtYV9hdXRob3JpemF0aW9uIiwicGxhdGZvcm1fYWRtaW4iXSwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJQbGF0Zm9ybSBBZG1pbiIsInByZWZlcnJlZF91c2VybmFtZSI6InBmX2FkbWluIiwiZ2l2ZW5fbmFtZSI6IlBsYXRmb3JtIiwiZmFtaWx5X25hbWUiOiJBZG1pbiIsImVtYWlsIjoicGZfYWRtaW5Ac29kYXMuZXRyaS5yZS5rciIsImdyb3VwIjpbIi9vcmdhbml6YXRpb24vZGVmYXVsdF9vcmcvcm9sZXMvcGxhdGZvcm1fYWRtaW4iXX0.CncQYSukT3XnqiX8R5t49T_4SW56-lUzIPIGiyFY2IT5bLbJ0MXNwiqhfz5LGisFTwtNk1kArPjS-ZrDZOToPCuOMSZ1KbVxQdA44wA3q1HxVJC0u0TSMhIHxCgHRFM7Lh0bxoDz9bvnkMAilmpaSaHAwm7f6z2agMHys1APpxuV_6hqHutsbsDDOm7Mk7V-nTueBnwtv_p4KhOSeOJfqJliuoufo9aiMeaB5LSEb3itaRIioBp5ylrFOi9ERt-D3ckbGKYsruuVtj29xbUavFETkjqifhbRR12O9aA0hmwcILDBm02q1eGhAmf2uTpio0RcOiElNchtA7d8Ki8nrw";

        KeycloakAdapter keycloakAdapter = new KeycloakAdapter();

        keycloakAdapter.verifyToken(token);

        rgwService.getFileList(key, bucketName, prefix);
    }

    @Operation(summary = "전송 속도 출력", description = "유저의 API 전송 속도와 호출 수를 출력합니다", responses = {
            @ApiResponse(responseCode = "200", description = "전송 속도 출력 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RateLimit.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @GetMapping("/permission/quota/user/rate-limit/{uid}")
    public ResponseEntity<?> getUserRateLimit(@Parameter(name = "uid", description = "유저 id") @PathVariable String uid,
                                              @GetIdFromToken Map<String, Object> userInfo) {

        if(rgwService.validAccess(userInfo, PF_ADMIN)){
            return ResponseEntity.ok(rgwService.getUserRateLimit(uid));
        }else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @Operation(summary = "전송 속도 제한", description = "API의 과도한 호출을 제한하기 위해 유저의 API 전송속도와 호출수를 제한합니다", responses = {
            @ApiResponse(responseCode = "200", description = "전송속도 제한 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RateLimit.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @PostMapping("/permission/quota/user/rate-limit/{uid}")
    public ResponseEntity<String> setUserRateLimit(@Parameter(name = "uid", description = "유저 id")@PathVariable String uid,
                                                   @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "제한 속도") @RequestBody RateLimit rateLimit,
                                                   @GetIdFromToken Map<String, Object> userInfo) {

        if(rgwService.validAccess(userInfo, PF_ADMIN)){
            return ResponseEntity.ok(rgwService.setUserRateLimit(uid, rateLimit));
        }else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

    }

    @Operation(summary = "prefix 경로의 폴더 및 파일 리스트 반환", description = "key 값과 버킷 이름, prefix을 입력하여 prefix 경로의 폴더 및 파일 리스트를 반환합니다", responses = {
            @ApiResponse(responseCode = "200", description = "prefix 경로의 폴더 및 파일 리스트 반환 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @PostMapping("/data/{bucketName}/files")
    public Map<String, List<?>> getFileList(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "해당 key 값")@RequestBody Key key,
                                            @Parameter(name = "bucketName", description = "버킷 이름")@PathVariable String bucketName,
                                            @Parameter(name = "prefix", description = "prefix")@RequestParam(required = false) String prefix){
        return rgwService.getFileList(key, bucketName, prefix);
    }

    /*
        Quota 반환 하기
        벼킷 각각의 크기 받아오기
     */
    @Operation(summary = "버킷 크기 조회", description = "버킷 이름을 입력하여 해당 버킷의 크기를 조회합니다", responses = {
            @ApiResponse(responseCode = "200", description = "버킷 크기 조회 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SQuota.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @GetMapping("/permission/quota/bucket/size/{bucketName}")
    public Map<String, Long> getIndividualBucketQuota(@Parameter(name = "bucketName", description = "버킷 이름") @PathVariable String bucketName) {
        return rgwService.getIndividualBucketQuota(bucketName);
    }

    /*
        버킷 각각의 크기 설정하기
     */
    @Operation(summary = "버킷 크기 설정", description = "유저 id와 버킷 이름, 할당량을 입력하여 버킷의 크기를 설정합니다", responses = {
            @ApiResponse(responseCode = "200", description = "버킷 크기 설정 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SQuota.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @PostMapping("/permission/quota/bucket/size/{bucketName}/{uid}")
    public ResponseEntity<SQuota> setIndividualBucketQuota(@Parameter(name = "bucketName", description = "버킷 이름") @PathVariable String bucketName,
                                                           @Parameter(name = "uid", description = "유저 id") @PathVariable String uid,
                                                           @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "할당량") @RequestBody SQuota quota,
                                                           @GetIdFromToken Map<String, Object> userInfo) {

        if(rgwService.validAccess(userInfo, PF_ADMIN)){
            return ResponseEntity.ok(rgwService.setIndividualBucketQuota(uid, bucketName, quota));
        }else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }


    /*
        서브 유저 생성
     */
    @Operation(summary = "서브 유저 생성", description = "유저 id를 입력하여 해당 유저의 서브 유저를 생성합니다", responses = {
            @ApiResponse(responseCode = "200", description = "서브 유저 생성 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SSubUser.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @PostMapping("/credential/user/{uid}/sub-user")
    public ResponseEntity<List<SubUser>> createSubUser(@Parameter(name = "uid", description = "유저 id") @PathVariable("uid") String uid,
                                                       @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "서브 유저") @RequestBody SSubUser subUser,
                                                       @GetIdFromToken Map<String, Object> userInfo) {
        if(rgwService.validAccess(userInfo, PF_ADMIN)){
            return ResponseEntity.ok(rgwService.createSubUser(uid, subUser));
        }else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /*
        서브 유저의 권한 정보 출력
     */
    @Operation(summary = "서브유저 권한정보 출력", description = "유저 id와 서브유저 id를 입력하여 해당 서브 유저의 권한정보를 출력합니다", responses = {
            @ApiResponse(responseCode = "200", description = "서브유저 권한정보 출력 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SSubUser.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @GetMapping("/credential/user/{uid}/sub-user/{subUid}")
    public ResponseEntity<String> subUserInfo(@Parameter(name = "uid", description = "유저 id") @PathVariable("uid") String uid,
                                              @Parameter(name = "subUid", description = "서브유저 id") @PathVariable("subUid") String subUid,
                                              @GetIdFromToken Map<String, Object> userInfo) {

        if(rgwService.validAccess(userInfo, PF_ADMIN)){
            return ResponseEntity.ok(rgwService.subUserInfo(uid, subUid));
        }else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /*
        서브 유저의 권한 수정
     */
    @Operation(summary = "서브유저 권한 수정", description = "유저 id와 서브유저 id를 입력하여 해당 서브 유저의 권한을 수정합니다", responses = {
            @ApiResponse(responseCode = "200", description = "서브유저 권한 수정 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SSubUser.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @PostMapping("/credential/user/{uid}/sub-user/{subUid}")
    public ResponseEntity setSubUserPermission(@Parameter(name = "uid", description = "유저 id") @PathVariable("uid") String uid,
                                               @Parameter(name = "subUid", description = "서브유저 id") @PathVariable("subUid") String subUid,
                                               @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "권한") @RequestBody String permission,
                                               @GetIdFromToken Map<String, Object> userInfo) {

        if(rgwService.validAccess(userInfo, PF_ADMIN)){
            rgwService.setSubUserPermission(uid, subUid, permission);
            return ResponseEntity.ok("Subuser permission set successfully.");
        }else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /*
        서브 유저 삭제
     */
    @Operation(summary = "서브 유저 삭제", description = "유저 id와 서브유저 id, key 값을 입력하여 해당 서브유저를 삭제합니다", responses = {
            @ApiResponse(responseCode = "200", description = "서브유저 삭제 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @PostMapping("/credential/user/{uid}/sub-user/{subUid}")
    public ResponseEntity<Object> deleteSubUser(@Parameter(name = "uid", description = "유저 id") @PathVariable("uid") String uid,
                                                @Parameter(name = "subUid", description = "서브유저 id") @PathVariable("subUid") String subUid,
                                                @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "해당 key 값")  @RequestBody Key key,
                                                @GetIdFromToken Map<String, Object> userInfo) {
        if(rgwService.validAccess(userInfo, PF_ADMIN)){
            rgwService.deleteSubUser(uid, subUid, key);
            return ResponseEntity.ok("Subuser permission set successfully.");
        }else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /*
        서브 유저의 엑세스키와 시크릿 키 변경
     */
    @Operation(summary = "서브유저 키 변경", description = "유저 id, 서브유저 id, key 값을 입력하여 서브 유저의 접근키와 비밀키를 변경합니다", responses = {
            @ApiResponse(responseCode = "200", description = "서브유저 키 변경 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SSubUser.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @PostMapping("/credential/user/{uid}/sub-user/{subUid}/key")
    public ResponseEntity<?> alterSubUserKey(@Parameter(name = "uid", description = "유저 id") @PathVariable("uid") String uid,
                                             @Parameter(name = "subUid", description = "서브유저 id") @PathVariable("subUid") String subUid,
                                             @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "해당 key 값") @RequestBody Key key,
                                             @GetIdFromToken Map<String, Object> userInfo) {

        if(rgwService.validAccess(userInfo, PF_ADMIN)){
            rgwService.alterSubUserKey(uid, subUid, key);
            return ResponseEntity.ok("Subuser permission set successfully.");
        }else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /*
       Credential - List
       uid를 파라미터로 받아 S3Credential list를 반환하는 api
     */
    @Operation(summary = "S3Credential 리스트 반환", description = "유저 id를 입력하여 S3Credential list를 반환합니다", responses = {
            @ApiResponse(responseCode = "200", description = "S3Credential 리스트 반환 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = S3Credential.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @GetMapping("/credential/user/{uid}")
    public ResponseEntity<?> getCredential(@Parameter(name = "uid", description = "유저 id") @PathVariable String uid,
                                            @GetIdFromToken Map<String, Object> userInfo) {

        if(rgwService.validAccess(userInfo, PF_ADMIN)){
            rgwService.getS3CredentialList(uid);
            return ResponseEntity.ok("Subuser permission set successfully.");
        }else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /*
        Credential - Create
        uid를 파라미터로 받아 S3Credential을 생성하는 api
     */
    @Operation(summary = "S3Credential 생성", description = "유저 id를 입력하여 S3Credential을 생성합니다", responses = {
            @ApiResponse(responseCode = "200", description = "S3Credential 생성 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = S3Credential.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @PostMapping("/credential/user/{uid}")
    public ResponseEntity<List<S3Credential>> createCredential(@Parameter(name = "uid", description = "유저 id") @PathVariable String uid,
                                               @GetIdFromToken Map<String, Object> userInfo) {

        if(rgwService.validAccess(userInfo, PF_ADMIN)){
            return ResponseEntity.ok(rgwService.createS3Credential(uid));
        }else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    // TODO: 자신의 subuser만 제어 가능하도록 valid access key 함수 넣어야 하는지?
    /*
        Credential - Delete
        uid와 key를 받아 S3Credential을 삭제하는 api
     */
    @Operation(summary = "S3Credential 리스트 삭제", description = "유저 id와 key 값을 입력하여 S3Credential list를 삭제합니다", responses = {
            @ApiResponse(responseCode = "200", description = "S3Credential 리스트 삭제 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @PostMapping("/credential/user")
    public ResponseEntity<?> deleteCredential(@Parameter(name = "uid", description = "유저 id") @PathVariable String uid,
                                              @Parameter(name = "key", description = "해당 key 값") @PathVariable Key key,
                                              @GetIdFromToken Map<String, Object> userInfo) {

        if(rgwService.validAccess(userInfo, PF_ADMIN)){
            rgwService.deleteS3Credential(uid, key.getAccessKey());
            return ResponseEntity.ok("Subuser permission set successfully.");
        }else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @Operation(summary = "서브유저 리스트 출력", description = "uid를 입력하여 해당 유저의 서브유저 리스트를 출력합니다")
    @GetMapping("/credential/user/sub-user/{uid}")
    public Map<String, String> subUserList(@Parameter(name = "uid", description = "유저 id")@PathVariable String uid) {
        return rgwService.subUserList(uid);
    }

    @PostMapping("/credential/user")
    public ResponseEntity<User> createUser(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "유저")@RequestBody SUser user,
                                           @GetIdFromToken Map<String, Object> userInfo) {

        if(rgwService.validAccess(userInfo, PF_ADMIN)){
            return ResponseEntity.ok(rgwService.createUser(user));
        }else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /*
        버킷 사용도 %로 계산하여 출력
    */
    @Operation(summary = "버킷 사용도 출력", description = "버킷 이름을 입력하여 해당 버킷의 사용도를 %로 출력합니다", responses = {
            @ApiResponse(responseCode = "200", description = "버킷 사용도 출력 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 리소스 접근")})
    @GetMapping("/monitoring/{bucketName}")
    public Double quotaUtilizationInfo(@Parameter(name = "bucketName", description = "버킷 이름") @PathVariable String bucketName) {
        return rgwService.quotaUtilizationInfo(bucketName);
    }

    @GetMapping("/quota/user/size")
    public Map<String, Map<String, Quota>> usersQuotaList(){
        return rgwService.usersQuota();
    }

    @GetMapping("/quota/user/rate-limit")
    public Map<String, Map<String, String>> usersRateLimit(){
        return rgwService.usersRateLimit();
    }

    @GetMapping("/quota/bucket/size")
    public Map<String, Map<String, Quota>> bucketsQuotaList(){
        return rgwService.bucketsQuota();
    }

    @GetMapping("/monitoring")
    public Map<String, Double> quotaUtilizationList(Key key) {
        return rgwService.quotaUtilizationList(key);
    }
}
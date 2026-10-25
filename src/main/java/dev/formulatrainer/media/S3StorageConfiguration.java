package dev.formulatrainer.media;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.media.storage-type", havingValue = "s3")
class S3StorageConfiguration {

    @Bean
    S3Client s3Client(MediaProperties properties) {
        MediaProperties.S3Properties s3 = properties.s3();
        if (s3 == null) {
            throw new IllegalStateException("S3 storage configuration is required");
        }

        URI endpoint = URI.create(required("S3_ENDPOINT", s3.endpoint()));
        if (!endpoint.isAbsolute()) {
            throw new IllegalStateException("S3_ENDPOINT must be an absolute URL");
        }
        required("S3_BUCKET", s3.bucket());

        return S3Client.builder()
            .endpointOverride(endpoint)
            .region(Region.of(required("S3_REGION", s3.region())))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        required("S3_ACCESS_KEY", s3.accessKey()),
                        required("S3_SECRET_KEY", s3.secretKey()))))
            .forcePathStyle(s3.pathStyleAccess())
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .build();
    }

    private static String required(String name, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required when MEDIA_STORAGE_TYPE=s3");
        }
        return value;
    }
}

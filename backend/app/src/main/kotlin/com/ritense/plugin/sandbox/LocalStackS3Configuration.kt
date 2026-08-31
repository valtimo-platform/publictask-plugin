/*
 * Copyright 2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ritense.plugin.sandbox

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CORSConfiguration
import software.amazon.awssdk.services.s3.model.CORSRule

// Credentials and a bucket for the LocalStack container. A real deployment has both already: dev only.
@Configuration
@Profile("dev")
class LocalStackS3Configuration {
    /** LocalStack accepts any credentials; this keeps the SDK from looking for real ones. */
    @Bean
    fun valtimoAwsCredentialsProviderChain(): AwsCredentialsProviderChain =
        AwsCredentialsProviderChain
            .builder()
            .addCredentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")),
            ).build()

    /** LocalStack starts empty. The CORS rule is what lets the browser upload with a pre-signed URL. */
    @Bean
    fun localStackS3BucketInitializer(
        s3Client: S3Client,
        @Value("\${aws.s3.bucketName}") bucketName: String,
    ) = CommandLineRunner {
        if (s3Client.listBuckets().buckets().none { it.name() == bucketName }) {
            s3Client.createBucket { it.bucket(bucketName) }
        }
        s3Client.putBucketCors { request ->
            request
                .bucket(bucketName)
                .corsConfiguration(
                    CORSConfiguration
                        .builder()
                        .corsRules(
                            CORSRule
                                .builder()
                                .allowedHeaders("*")
                                .allowedMethods("GET", "PUT", "POST", "DELETE", "HEAD")
                                .allowedOrigins("*")
                                .exposeHeaders("ETag")
                                .build(),
                        ).build(),
                )
        }
    }
}

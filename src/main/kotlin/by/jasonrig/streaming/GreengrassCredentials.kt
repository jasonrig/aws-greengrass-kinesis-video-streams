package by.jasonrig.streaming

import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import java.time.Instant

/**
 * Contains the temporary credentials that can be used by AWS services
 * @param accessKeyId the AWS Access Key
 * @param secretAccessKey the AWS Secret Access Key
 * @param sessionToken the AWS Session Token
 * @param expiry the expiry date of this token
 */
class GreengrassCredentials(
    accessKeyId: String,
    secretAccessKey: String,
    sessionToken: String,
    val expiry: Instant
) : AwsCredentials {

    private val sessionCredentials: AwsSessionCredentials =
        AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken)


    /**
     * @see AwsSessionCredentials.accessKeyId
     */
    override fun accessKeyId(): String {
        return sessionCredentials.accessKeyId()
    }

    /**
     * @see AwsSessionCredentials.secretAccessKey
     */
    override fun secretAccessKey(): String {
        return sessionCredentials.secretAccessKey()
    }

    /**
     * @see AwsSessionCredentials.sessionToken
     */
    fun sessionToken(): String {
        return sessionCredentials.sessionToken()
    }
}
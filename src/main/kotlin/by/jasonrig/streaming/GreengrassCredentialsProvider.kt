package by.jasonrig.streaming

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.OffsetDateTime


/**
 * Provides a temporary credential for use by the Kinesis Stream producer
 * We could normally use the ContainerCredentialsProvider, but it does not give the token expiry date,
 * so a custom implementation is used instead.
 * @see software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
 */
class GreengrassCredentialsProvider : AwsCredentialsProvider {

    // The URLs provided by the AWS IoT Greengrass Java runtime
    private val credentialsUrl: String = System.getenv("AWS_CONTAINER_CREDENTIALS_FULL_URI")
    private val credentialsUrlAuthToken: String = System.getenv("AWS_CONTAINER_AUTHORIZATION_TOKEN")

    companion object {
        private val gson: Gson = GsonBuilder().create()
    }

    override fun resolveCredentials(): AwsCredentials {
        // Execute HTTP GET request to the credentials endpoint
        val response = requestCredentialsFromEndpoint()

        // Ensure a 2xx status code was returned (anything else and the request failed)
        require(response.first / 100 == 2) {
            "Got status code ${response.first}, but expected 2xx: ${response.second}"
        }

        // Parse the JSON response
        val credentialsResponse = parseJson(response.second)

        // Convert the response to a `GreengrassCredentials` object, which subclasses AwsCredentials
        // The response is guaranteed to provide the Access Key Id, Secret Access Key, Secret Token, and Expiration date
        return GreengrassCredentials(
            credentialsResponse.accessKeyId ?: error("No access key returned from AWS credentials endpoint"),
            credentialsResponse.secretAccessKey ?: error("No secret access key returned from AWS credentials endpoint"),
            credentialsResponse.token ?: error("No session token returned from AWS credentials endpoint"),
            OffsetDateTime.parse(
                credentialsResponse.expiration ?: error("No expiration returned from AWS credentials endpoint")
            ).toInstant()
        )
    }

    /**
     * Parses the response string to an object with all fields from the returned JSON object
     * @param responseText a string containing the HTTP response
     * @return a parsed object containing the result
     */
    private fun parseJson(responseText: String): CredentialsResponse {
        val credentialsResponseType = object : TypeToken<CredentialsResponse>() {}.type
        return gson.fromJson(responseText, credentialsResponseType)
    }

    /**
     * Performs the HTTP GET request to get the temporary credential as a JSON object
     * @return a Pair of status code and JSON string
     */
    private fun requestCredentialsFromEndpoint(): Pair<Int, String> {
        val httpclient = HttpClients.createDefault()
        val httpget = HttpGet(credentialsUrl)
        httpget.setHeader("Content-type", "application/json")
        httpget.setHeader("Authorization", credentialsUrlAuthToken)
        val httpresponse: HttpResponse = httpclient.execute(httpget)

        val reader = BufferedReader(
            InputStreamReader(
                httpresponse.entity.content,
                "UTF-8"
            )
        )

        return Pair(
            httpresponse.statusLine.statusCode,
            reader.lineSequence().joinToString("\n")
        )
    }

    /**
     * The class in which the JSON response is marshalled
     */
    private class CredentialsResponse {
        @SerializedName("AccessKeyId")
        val accessKeyId: String? = null

        @SerializedName("Expiration")
        val expiration: String? = null

        @SerializedName("RoleArn")
        val roleARN: String? = null

        @SerializedName("SecretAccessKey")
        val secretAccessKey: String? = null

        @SerializedName("Token")
        val token: String? = null
    }
}
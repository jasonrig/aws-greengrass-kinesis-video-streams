package by.jasonrig.streaming

import org.apache.commons.cli.*
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider


/**
 * Entry point if not run as AWS Lambda function
 * @param args the command line arguments
 */
fun main(args: Array<String>) {
    val options = Options()

    // CLI options
    options.addOption("d", "device", true, "camera device path")
    options.addOption("s", "stream", true, "stream name")
    options.addOption("r", "region", true, "AWS region")

    // Parse the CLI options
    val parser: CommandLineParser = DefaultParser()
    val cmd: CommandLine = parser.parse(options, args)

    // Try to start the stream. If an IllegalStateException is thrown, print the CLI help information
    try {

        // Get the configuration parameters from the parsed CLI options
        // If anything is null when it shouldn't be, IllegalStateException will be thrown
        val streamParameters = Streamer.ConnectionParameters(
            videoDevice = cmd.getOptionValue("d"),
            streamName = cmd.getOptionValue("s"),
            awsRegion = cmd.getOptionValue("r", "ap-northeast-1")
        )

        // Instantiate the Streamer object and begin streaming
        val s = Streamer(
            streamParameters,
            EnvironmentVariableCredentialsProvider.create(),
            null
        )
        s.start()
    } catch (e: IllegalStateException) {
        val formatter = HelpFormatter()
        formatter.printHelp("stream", options)
    }
}
# email-tagging

## Run Instructions
Requirements:
- Java >= 1.8 (https://java.com/en/download/help/download_options.xml)
- Gradle >= 2.3 (http://gradle.org/downloads)
- A Google account with Gmail enabled

To run this program, invoke `./gradlew run` from the root directory

To access this program once it is running, navigate your web browser to <localhost:8080>

## Description
This program returns the ids of Gmail messages believed to contain a link to a file sharing site,
as well as some basic statistics.

The first time you run this program, you will need to authenticate into your Gmail account using
Google OAuth. If you need to switch which account is being used, delete the
[tokens/StoredCredential](tokens/StoredCredential) file.

## Future Improvements
- Collect a comprehensive list of file sharing websites/link URL patterns and add them to [file_sharing_link_matches.txt](src/main/resources/file_sharing_link_matches.txt)
- Prettify the output webpage
- Allow user to deep dive on specific results, e.g.
    - See more details about a matching email
    - Restrict results to a specific issue type
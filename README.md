# IRMA MNO server

A self-enrollment server for IRMA credentials using passports, identity cards or drivers licenses with NFC chips. It is meant to work together with the [card emulator app](https://github.com/credentials/irma_android_cardemu), which extracts some information from the document and sends it to this server. We then check the validity of the document, extract some personal information from it, and ask the [IRMA API server](https://github.com/credentials/irma_api_server) to issue some credentials containing the extracted information.


## Configuring the server

The server needs a running and correctly configured [API server](https://github.com/credentials/irma_api_server) instance to do the credential issuing. The URL and credentials for this API server can be configured using a json file at `src/main/resources/config.json`. In the same directory a sample configuration file called `config.sample.json` is included, showing all options, their defaults, and what they mean.

To be able to also process portaits of older passport types the image tool GraphicsMagick (http://www.graphicsmagick.org/download.html) is required. Without this tool the application will only support passports with BMP images. The tool must be accessible from PATH. In Debian/Ubuntu GraphicsMagick can be installed via:

    sudo apt-get install python-software-properties
    sudo apt-get install software-properties-common
    sudo add-apt-repository ppa:rwky/graphicsmagick
    sudo apt-get update
    sudo apt-get install graphicsmagick

## Running the server

The gradle build file should take care the dependencies. To run the server in development mode simply call:

    gradle appRun

## Testing with cURL

To make a GET request on a resource:

    curl -i -H "Accept: application/json" http://localhost:8080/irma_mno_server/api/hello/json

To make a POST request on a resource:

    curl -X POST -H "Content-Type: application/json" -d '{"a": 5.0,"b": -22.0}' http://localhost:8080/irma_mno_server_jersey/api/hello/json

## Server API

The following describes the API offered by the server to an MNO enrollment client. Currently the server supports enrolling with passports and identity cards (which behave identically), and electronic drivers licenses.

### start

To initiate a self-enrollement the client first needs to create a session with the server. The server will also immediately supply the client with the necessary nonce for the active authentication. Below, whenever `document` occurs in an URL, it must be replaced with either `passport` or `dl`.

`GET https://<server>/api/v2/document/start`

Input: (none)

Output:

 * sessionToken: a string encoding the session token
 * nonce: a Base64 encoded nonce for the active authentication

### verify-passport

The client retrieves the necessary data, the signatures and the active authentication response from the passport and returns them to the server.

`POST https://<server>/<api>/v2/document/verify-document`

Input: an `EDLDataMessage` or `PassportDataMessage` from the [mno_common](https://github.com/credentials/irma_mno_common/) project.

Output:
 * One of the following six status values:
   * `SUCCESS`: The document was valid and the data for the credentials was successfully extracted.
   * `PASSPORT_INVALID`: The passport was expired or reported stolen.
   * `HASHES_INVALID`, `SIGNATURE_INVALID`, `AA_FAILED`: The passport data itself was incorrect.
   * `NOT_FOUND`: In a closed subscription model, this can be used to indicate that the document owner was not found in the subscriber database.
 * If the status was `SUCCESS`, a URL to an [API server](https://github.com/credentials/irma_api_server) instance that will continue issuance of the credentials.

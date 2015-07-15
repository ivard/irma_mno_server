# Running the server

The gradle build file should take care of most of the dependencies. However, `irma_mno_common` is not yet available in the central IRMA repository, so you'll have to manually download and install it. To run the server in development mode simply call:

    gradle jettyRun

it is set up in such a way that it will automatically reload recompile class files. If your IDE already uses gradle to compile them this should work out of the box. Otherwise, simply call

    gradle javaCompile

and your app will be reloaded. Note that this is a lot faster than simply restarting the Jetty container.

# Testing with cURL

To make a GET request on a resource:

    curl -i -H "Accept: application/json" http://localhost:8080/irma_mno_server/api/hello/json

To make a POST request on a resource:

    curl -X POST -H "Content-Type: application/json" -d '{"a": 5.0,"b": -22.0}' http://localhost:8080/irma_mno_server_jersey/api/hello/json

## Notes about serializing classes

You need to have getters to get the fields, and setters to actually be able to reconstruct the JSON when it is supplied in a POST message.

# Server API

The following describes the API offered by the server to an MNO enrollment client. All calls except `start` require the client to pass in its sessionToken. If the session token is unknown, or has expired, the server will return a

 * 401 UNAUTHORIZED if sessionToken is unknown or expired

Admittedly, this is a bit of an abuse of HTTP status codes, but this one most closely matches the intended meaning. Similarly, the client can send malformatted POST requests, in which case the server will return

 * 400 BAD REQUEST if the POSTed JSON is incorrect

Finally, the server operates a strict state engine. If requests are sent out of order the server also replies with a

 * 401 UNAUTHORIZED if sessionToken is unknown or expired

## start

To initiate a self-enrollement the client first needs to create a session with the server. The server will also immediately supply the client with the necessary nonce for the active authentication.

GET https://<server>/api/v1/start

Inputs: (none)

Outputs:

 * sessionToken: a string encoding the session token
 * nonce: a Base64 encoded nonce for the active authentication

Throws:
 * 401 UNAUTHORIZED (bit of an abuse case) if sessionToken is unknown

## verify-passport

*TODO: incomplete*

The client retrieves the necessary data, the signatures and the active authentication response from the passport and returns them to the server. The server will check the result of this authentication and returns one of the following four values:

 * success: the data is valid, and the subscriber found in the database
 * not_found: the data is valid, but the subscriber was not found in the database
 * passport_invalid: the passport data itself was incorrect, the passport expired or the passport was reported stolen
 * aa_failed: the active authentication of the passport failed

POST https://<server>/<api>/v1/verify-passport

Inputs:

 * sessionToken: the sessionToken as obtained during `start`
 * imsi: the phone's imsi, *FIXME* encoded as something
 * dg1: Raw representation of datagroup 1
 * dg15: Raw representation of datagroup 15
 * sodfile: The reponse to the active authentication request

(The encodings for `dg1`, `dg15` and `sodfile` correspond to JMRTD's byte array representation of the corresponding files.)

Output:

 * result: success/not_found/passport_invalid/aa_failed

## issue/credential-list

After a successful validation of the passport data (the `verify-passport` call returned `success`) the server can issue credentials to the client based on the passport data. The `issue/credential-list` method can be called to retrieve a list of the credentials the server can issue, together with the desired attributes.

POST https://<server>/<api>/v1/issue/credential-list

Inputs:

 * sessionToken: the sessionToken as obtained during `start`

this corresponds to the `BasicClientMessage` class in `irma_mno_common`.

Output:

A JSON object of credentials the credential's name is the name of the pair, whereas a JSON object representing the attributes is the value. The JSON object representing the attributes is an object of name-value pairs where the name is the attribute name and the value the string encoding of the attribute. Example:

    {
      "root" : {
        "BSN" : "123456789"
      },
      "ageLower" : {
        "over12" : "yes",
        "over18" : "yes",
        "over21" : "yes",
        "over16" : "yes"
      }
    }

## issue/{cred}/start

Each of the credentials named in `issue/credential-list` can be issued. To start the issuance process the client makes a call to `issue/{cred}/start` where `{cred}` is the named credential. For example the start issuing the root credential named in the previous example, the client makes a call to `issue/root/start`. This request is APDU based

POST https://<server>/<api>/v1/issue/{cred}/start

Inputs:

 * sessionToken: the sessionToken as obtained during `start`
 * cardVersion: the Base64 encoding of the card's response to the select command

this corresponds to the `RequestStartIssuanceMessage` class in `irma_mno_common`.

Output:

An array of CommandAPDUs that the client needs to send to the card (in this order). Every CommandAPDU is a JSON object containing:

 * key: an identifier for the command
 * command: a Base64 encoding of the APDU that needs to be send to the card.

this corresponds to the `ProtocolCommands` class from Scuba.

## issue/{cred}/finish

To finish the issuance process, the client needs to post the card's responses to the first batch of issuance messages. The `{cred}` parameter is as before.

POST https://<server>/<api>/v1/issue/{cred}/finish

Inputs:

 * sessionToken: the sessionToken as obtained during `start`
 * responses: a JSON object representing the protocol responses

The responses object contains the command's key as name, whereas the value contains:

 * key: The response's key again
 * apdu: The Base64 encoded response APDU (including the two status bytes)

This corresponds to the `RequestFinishIssuanceMessage` class in `irma_mno_common`.

Outputs:

As for `issue/{cred}/start` an array of CommandAPDUs that need to be send to the card.

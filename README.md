# Dependencies

The gradle build file should take care of most of the dependencies. However, `irma_mno_common` is not yet available in the central IRMA repository, so you'll have to manually download and install it. To run the server in development mode simply call:

    gradle jettyRun

it is set up in such a way that it will automaticaly reload recompile class files. If your IDE alreay uses gradle to compile them this should work out of the box. Otherwise, simply call

    gradle javaCompile

and your app will be reloaded. Note that this is a lot faster than simply restarting the Jetty container.

# Testing with cURL

To make a GET request on a resource:

    curl -i -H "Accept: application/json" http://localhost:8080/irma_mno_server/api/hello/json

To make a POST request on a resource:

    curl -X POST -H "Content-Type: application/json" -d '{"a": 5.0,"b": -22.0}' http://localhost:8080/irma_mno_server_jersey/api/hello/json

## Notes about serializing classes

You need to have getters to get the fields, and setters to actually be able to reconstruct the JSON when it is supplied in a POST message.

# Protocol

## start-passport-verification

GET https://<server>/api/v1/enroll/start

Inputs: (none)

Outputs:

 * sessionToken: a FIXME encoded session token
 * nonce: a Base64 encoded nonce for the active authentication

Throws:
 * 401 UNAUTHORIZED (bit of an abuse case) if sessionToken is unknown

## verify-passport

POST https://<server>/<api>/v1/verify-passport

Inputs:

 * IMSI: the phone's imsi, FIXME encoded (string?)
 * FIXME passport data
 * FIXME response to nonce

Output:

 * result: success/not_found/passport_invalid/aa_failed

# Current set of test-vectors

    curl -i -H "Accept: application/json" http://localhost:8080/irma_mno_server/api/v1/start

    curl -X POST -H "Content-Type: application/json" -d '{"sessionToken": "sALu5XeClQ2y5gipOKmCtz9v52Uh9ShmNiAOxtPasUx", "imsi": "1234567890"}' http://localhost:8080/irma_mno_server/api/v1/verify-passport

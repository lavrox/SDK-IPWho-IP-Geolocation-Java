package com.ipwho;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Reads one bulk result. The API sends each row flat:
 * {@code {success, ip, geoLocation, ...}} or {@code {success: false, ip, message}}.
 * A row already nested under {@code data} is accepted too.
 */
class BulkRowDeserializer extends JsonDeserializer<IpGeoResponse> {
    @Override
    public IpGeoResponse deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectCodec codec = parser.getCodec();
        JsonNode row = codec.readTree(parser);
        if (row.has("data")) {
            return codec.treeToValue(row, IpGeoResponse.class);
        }
        IpGeoResponse response = new IpGeoResponse();
        response.setSuccess(row.path("success").asBoolean(false));
        response.setMessage(row.hasNonNull("message") ? row.get("message").asText() : null);
        response.setData(codec.treeToValue(row, IpGeoResponse.GeoData.class));
        return response;
    }
}

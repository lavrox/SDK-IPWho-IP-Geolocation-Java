# IPWho ([ipwho.org](https://www.ipwho.org)) Java SDK

[![Java version](https://img.shields.io/badge/java-11+-orange.svg)](https://www.java.com/) [![license](https://img.shields.io/badge/license-MIT-green.svg)](https://github.com/lavrox/SDK-IPWho-IP-Geolocation-Java/blob/main/LICENSE)

Official Java client for the [IPWho](https://www.ipwho.org) IP Geolocation API. One call returns the **full** payload: geolocation, timezone, flag, currency, connection (ASN/ISP), security, and user-agent when present.

- Product: [ipwho.org](https://www.ipwho.org)
- API docs: [ipwho.org/docs](https://www.ipwho.org/docs)
- Get an API key: [ipwho.org/free-plan](https://www.ipwho.org/free-plan) (free [Lavrox](https://lavrox.com) account)
- Live API host: `https://api.ipwho.org`

## API key

Open a free [Lavrox](https://lavrox.com) account to get an API key for [IPWho](https://www.ipwho.org). Create your key at [ipwho.org/free-plan](https://www.ipwho.org/free-plan) — no credit card required.

## Installation

Maven:

```xml
<dependency>
    <groupId>com.lavrox</groupId>
    <artifactId>ipwho</artifactId>
    <version>1.0.0</version>
</dependency>
```

Gradle: `implementation("com.lavrox:ipwho:1.0.0")`

Java 11+. HTTP uses Java's built-in `HttpClient`; the one library dependency is Jackson (`jackson-databind`), which reads the JSON. Package: `com.ipwho`.

## Quick Start

```java
IpWhoClient client = new IpWhoClient(System.getenv("IPWHO_API_KEY"));

IpGeoResponse resp = client.lookup("8.8.8.8");          // GET /ip/{ip}
IpGeoResponse me = client.me();                         // GET /me
List<IpGeoResponse> bulk = client.bulk(Arrays.asList("8.8.8.8", "1.1.1.1"));
```

```
IpGeoResponse
├── isSuccess()
├── getMessage()
└── getData()  GeoData
    ├── getIp()
    ├── getGeoLocation()
    ├── getTimezone()
    ├── getFlag()
    ├── getCurrency()
    ├── getConnection()
    ├── getSecurity()
    └── getUserAgent()
```

## Reading the full response (8.8.8.8)

Live [IPWho](https://www.ipwho.org) values: United States, ASN 15169, America/Chicago, dial code +1. Nested objects may be `null`.

```java
IpGeoResponse.GeoData data = client.lookup("8.8.8.8").getData();
System.out.println(data.getIp()); // 8.8.8.8

IpGeoResponse.GeoLocation geo = data.getGeoLocation();
System.out.println(geo.getCountry());      // United States
System.out.println(geo.getCountryCode());  // US
System.out.println(geo.getContinent());
System.out.println(geo.getContinentCode());
System.out.println(geo.getCapital());
System.out.println(geo.getRegion());
System.out.println(geo.getRegionCode());
System.out.println(geo.getCity());
System.out.println(geo.getPostalCode());
System.out.println(geo.getDialCode());     // +1
System.out.println(geo.getIsInEu());
System.out.println(geo.getLatitude());
System.out.println(geo.getLongitude());
System.out.println(geo.getAccuracyRadius()); // e.g. 1000

IpGeoResponse.Timezone tz = data.getTimezone();
System.out.println(tz.getTimeZone()); // America/Chicago
System.out.println(tz.getAbbr());
System.out.println(tz.getOffset());
System.out.println(tz.getIsDst());
System.out.println(tz.getUtc());
System.out.println(tz.getCurrentTime());

System.out.println(data.getFlag().getFlagIcon());    // 🇺🇸
System.out.println(data.getFlag().getFlagUnicode()); // U+1F1FA U+1F1F8

if (data.getCurrency() != null) {                           // null for some addresses, such as 1.1.1.1
    System.out.println(data.getCurrency().getCode());
    System.out.println(data.getCurrency().getNamePlural()); // US dollars
}

IpGeoResponse.Connection conn = data.getConnection();
System.out.println(conn.getAsnNumber());      // 15169
System.out.println(conn.getAsnOrg());         // Google LLC
System.out.println(conn.getIsp());
System.out.println(conn.getOrg());
System.out.println(conn.getDomain());
System.out.println(conn.getConnectionType()); // Corporate

System.out.println(data.getSecurity().getIsVpn());
System.out.println(data.getSecurity().getIsTor());
System.out.println(data.getSecurity().getIsThreat());

if (data.getUserAgent() != null) {
    System.out.println(data.getUserAgent().getBrowser().getName());
}

System.out.println(client.me().getData().getIp());

for (IpGeoResponse row : client.bulk(Arrays.asList("8.8.8.8", "1.1.1.1"))) {
    if (row.isSuccess()) {
        System.out.println(row.getData().getIp() + " " + row.getData().getGeoLocation().getCountry());
    } else {
        System.out.println(row.getData().getIp() + " " + row.getMessage());
    }
}
```

Overloads: `lookup(ip, format, fields)`, `me(format, fields)` (`json`/`xml`/`csv`).

## API Reference

### `new IpWhoClient(String apiKey)`

Also `IpWhoClient(apiKey, baseUrl, httpClient)` with a `java.net.http.HttpClient`, and `IpWhoClient(apiKey, baseUrl, httpClient, requestTimeout)`. Each request times out after 30 seconds by default. Empty key → `IllegalArgumentException`. The key is sent as `Authorization: Bearer`.

### Errors

`NotFoundException` (404), `RateLimitException` (429), `IpWhoException`.

## Type Definitions

Getters on nested static classes of `IpGeoResponse`:

- **GeoLocation**: continent, continentCode, country, countryCode, capital, region, regionCode, city, postalCode, dialCode, isInEu, latitude, longitude, accuracyRadius
- **Timezone**: timeZone, abbr, offset, isDst, utc, currentTime
- **Flag**: flagIcon, flagUnicode
- **Currency**: code, symbol, name, namePlural, hexUnicode
- **Connection**: asnNumber, asnOrg, isp, org, domain, connectionType
- **Security**: isVpn, isTor, isThreat
- **UserAgent**: browser, engine, os, device, cpu

Jackson maps mixed wire keys (`postal_Code`, `flag_Icon`, `isVpn`).

## Troubleshooting

- Key: [ipwho.org](https://www.ipwho.org).
- HTTP 403: SDK sends `ipwho-java-sdk/1.0.0`.
- Null nested objects on some IPs.

## Testing

```bash
./gradlew test
```

These tests need no API key: they run against a local stand-in server. `examples/TestMain.java` is a live check, which uses lookups from your quota. Run it with `IPWHO_API_KEY` set and the library and Jackson on the classpath.

## Changelog

### v1.0.0

- `lookup`, `me`, `bulk` matching [api.ipwho.org](https://api.ipwho.org)

## License

MIT License — see [LICENSE](LICENSE).

## Support

- Documentation: [ipwho.org/docs](https://www.ipwho.org/docs)
- Contact: [ipwho.org/contact](https://www.ipwho.org/contact)
- GitHub Issues: [lavrox/SDK-IPWho-IP-Geolocation-Java](https://github.com/lavrox/SDK-IPWho-IP-Geolocation-Java/issues)
- Website: [ipwho.org](https://www.ipwho.org)

---

[IPWho](https://www.ipwho.org) — a [Lavrox](https://lavrox.com) network API.

[Lavrox](https://lavrox.com) — Independent API infrastructure. Lower latency, lower cost.

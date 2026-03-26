# Header Stripper Burp Extension

This extension watches Burp traffic, builds a live inventory of unique HTTP headers, and lets you check headers that should be removed automatically.

## What It Does

- Learns unique request and response headers from live traffic.
- Shows them in a checkbox table with request count, response count, and last-seen tool.
- Removes checked headers automatically from Proxy traffic.
- Defaults to stripping Proxy requests only, with Proxy responses optional.
- Includes an extra toggle to also rewrite the stored/original Proxy request while still keeping Burp's normal `Original` versus `Edited` interception behavior available.
- Includes filter, clear, uncheck-all, and common-sensitive-header quick-select actions.

## Practical Features Implemented

- Case-insensitive exact header-name matching.
- Separate toggles for learning from requests and responses.
- Separate toggles for stripping Proxy requests and Proxy responses.
- Separate toggle for also rewriting the stored/original Proxy request.
- Quick selection of common sensitive headers such as `Authorization`, `Cookie`, and `X-Forwarded-For`.

## Build

```bash
./build.sh
```

If your Burp API JAR is elsewhere:

```bash
BURP_API_JAR=/path/to/burp-extender-api.jar ./build.sh
```

The build output is:

```text
dist/header-stripper-burp-extension.jar
```

## Load In Burp

1. Open `Extender`.
2. Choose `Extensions`.
3. Click `Add`.
4. Set extension type to `Java`.
5. Select `dist/header-stripper-burp-extension.jar`.

Burp should load `io.github.burpextensions.headerstripper.HeaderStripperBurpExtender`.

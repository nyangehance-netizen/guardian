# iOS build notes (Info.plist keys)

Add these keys to the app target's Info.plist (Xcode: target > Info):

| Key | Value |
|-----|-------|
| `NSLocationWhenInUseUsageDescription` | "Guardian shares this phone's location with your parent." |
| `NSLocationAlwaysAndWhenInUseUsageDescription` | "Guardian shares this phone's location with your parent, including in the background." |
| `UIBackgroundModes` | array containing `location` |

The app transport security default blocks plain HTTP. For local testing against the
`http://` backend, either run the backend behind HTTPS, or add an ATS exception for
your backend host during development only.

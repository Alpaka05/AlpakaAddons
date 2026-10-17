# Versions

Do not change the version when making code, feature, or configuration changes. There is no
`mod_version` in `gradle.properties` any more: the version is read from the git tags at build time
(see `gitVersion()` in `build.gradle`), and builds between releases name themselves after the
commit they were built from.

A release is a git tag such as `1.4.0` (or a pre-release such as `2.0.0-beta.1`), and only the maintainer decides when one is made. Never
create, move, or push a release tag unless explicitly asked to.

`version_line` in `gradle.properties` names the next major version a branch works towards; leave it
as it is unless asked.

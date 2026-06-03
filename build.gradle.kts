// Root build. Artifacts are distributed as GitHub Release assets (see RELEASING.md);
// there is no Maven Central / Sonatype publishing.

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

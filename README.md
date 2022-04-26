# GenerateEverythingPlugin

I've used InnerBuilder as a base here's the code - https://github.com/analytically/innerbuilder

I've developed this as I hated the amount of clicks required to generate all this stuff. This will generate up to 5 constructors for your class; EMPTY_CONSTRUCTOR, ALL_ARGS_CONSTRUCTOR, SUPER_OBJECT_CONSTRUCTOR, SUPER_ARGS_CONSTRUCTOR, ALL_ARGS_SUPER_CONSTRUCTOR, it will generate all the getters & setters for you and a toString() method which calls super.toString() if that exists.

Builds are here: https://plugins.jetbrains.com/plugin/13406-generate-everything/

PUBLISH_TOKEN required as a system environment variable to publish to jetbrains plugins.
Github now wants a token rather than password to push - https://github.com/settings/tokens
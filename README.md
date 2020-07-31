# GenerateEverythingPlugin

As this is the first plugin I was building and I had no idea how to build a plugin I've used InnerBuilder as a base and learned a lot of lessons from that. The code is Apache 2.0 so I'm pretty sure I can modify/redistribute it with attribution, here's the attribution - https://github.com/analytically/innerbuilder .

I've developed this as I hated the amount of clicks required to generate all this stuff. This will generate up to 4 constructors for your class, a no-args, a super (if applicable), a super with args and an all args without super constructor, it will generate all the getters & setters for you and a toString() method which calls super.toString() if that exists.

I now have some idea what I'm doing with PSI stuff. Feel free to contribute.

# Third-party notices

The MIT license at the repository root applies to the project's own source,
translation dictionary, scripts, and documentation. It does not replace the
licenses of the components listed below or license the Phon application.

## ASM classes distributed in phon-zh-agent.jar

The build copies the `groovyjarjarasm/asm/` class namespace from the user's
installed Phon 4.1.1 dependency `groovy-4.0.30.jar`. It does not distribute
the whole Groovy library.

ASM is a Java bytecode manipulation framework, copyright (c) 2000–2011 INRIA,
France Telecom. Its BSD 3-Clause license is reproduced in
[`licenses/ASM-LICENSE.txt`](licenses/ASM-LICENSE.txt) and inside the agent JAR
at `META-INF/licenses/asm-license.txt`.

The Groovy distribution's Apache license and notice are preserved in
[`licenses/GROOVY-LICENSE.txt`](licenses/GROOVY-LICENSE.txt) and
[`licenses/GROOVY-NOTICE.txt`](licenses/GROOVY-NOTICE.txt), as well as in the
agent JAR at `META-INF/LICENSE` and `META-INF/NOTICE`.

## External runtime

Phon and its bundled Java runtime, native-dialogs, fonts, and other libraries
must be installed separately. They are not included in this repository or
release archive. The patch uses the existing installation at runtime.

Sources consulted for public presentation APIs:

- https://github.com/phon-ca/phon — earlier Phon source, Apache License 2.0
- https://github.com/ghedlund/nativedialogs — native-dialogs, Apache License 2.0

Screenshots document the appearance of the externally installed Phon
application. They are not a redistribution of the application binary or its
source code.

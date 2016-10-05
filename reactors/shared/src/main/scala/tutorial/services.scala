/*!md
---
layout: tutorial
title: Reactor System Services
topic: reactors
logoname: reactress-mini-logo-flat.png
projectname: Reactors.IO
homepage: http://reactors.io
permalink: /reactors/services/index.html
pagenum: 4
pagetot: 40
section: guide
---
!*/


/*!md
## Services

In the earlier sections,
we learned that reactors delimit concurrent executions,
and that event streams allow routing events within each reactor.
This is already a powerful set of abstractions,
and we can use reactors and event streams to write all kinds of distributed programs.
However, such a model is restricted to reactor computations only --
we cannot, for example, start blocking I/O operations, read from a temperature sensor,
wait until a GPU computation completes, or do logging.
In some cases,
we need to interact with the native capabilities of the OS,
or tap into a rich ecosystem of existing libraries.
For this purpose,
every reactor system has a set of **services** --
protocols that relate event streams to the outside world.

In this section,
we will take a closer look at various services that are available in a reactor system,
and also show how to implement and plug-in custom services.
!*/

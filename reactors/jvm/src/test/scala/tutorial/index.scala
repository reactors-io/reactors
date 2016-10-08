/*!md
---
layout: tutorial
title: Reactors
topic: reactors
logoname: reactress-mini-logo-flat.png
projectname: Reactors.IO
homepage: http://reactors.io
permalink: /reactors/index.html
---


To get started with Reactors.IO, you should grab the latest snapshot version distributed
on Maven. If you are using SBT, add the following to your project definition:

```scala
resolvers ++= Seq(
  "Sonatype OSS Snapshots" at
    "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype OSS Releases" at
    "https://oss.sonatype.org/content/repositories/releases"
)

libraryDependencies ++= Seq(
  "io.reactors" %% "reactors" % "0.8-SNAPSHOT")
```

If you are using Scala.js, use the following dependency:

```scala
libraryDependencies ++= Seq(
  "io.reactors" %%% "reactors" % "0.8-SNAPSHOT")
```

Examples in this tutorial work with multiple language frontends: Java, Scala and
Scala.js. By default, code snippets are shown using the Scala syntax,
which is equivalent to Scala.js (modulo a few platform-specific differences,
which are noted in respective cases).
To show the Java version of each snippet, click on the toggle found below the code.

The getting started guide has the following parts:

{% for pg in site.pages %}
  {% if pg.topic == "reactors" and pg.section == "guide" and pg.pagetot %}
    {% assign totalPages = pg.pagetot %}
  {% endif %}
{% endfor %}

<ul>
{% for i in (1..totalPages) %}
  {% for pg in site.pages %}
    {% if pg.topic == "reactors" and pg.section == "guide" %}
      {% if pg.pagenum and pg.pagenum == i %}
        <li><a href="/tutorialdocs/{{ pg.url }}">{{ pg.title }}</a></li>
      {% endif %}
    {% endif %}
  {% endfor %}
{% endfor %}
</ul>

!*/
package tutorial

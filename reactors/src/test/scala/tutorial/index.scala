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

    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at
        "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype OSS Releases" at
        "https://oss.sonatype.org/content/repositories/releases"
    )

    libraryDependencies ++= Seq(
      "com.storm-enroute" %% "reactors" % "0.7-SNAPSHOT")

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

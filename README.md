# Spring XD Regular Expression Module
*Transform a string into a Tuple using a Regular Expression.*

## Overview

This tiny module (single class) can be added to an XD Stream to transform a string based payload into a multi-field Tuple, where the field names and values are the regular expression capture group name/number and matching value respectively. 

## Download

This library is available via <a href="http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.allenru%22%20a%3A%22xd-regex-module%22">the maven central repository</a> at the following coordinates:

```
    <groupId>com.allenru</groupId>
    <artifactId>xd-regex-module</artifactId>
```

## Usage

## Known Issues

This is alpha code.  The internals work.  I've run this as an XD module in the past, but I've not setup an XD server locally to test this build.  I expect that this is **NOT A VALID MODULE.**  I'm pushing this into the public domain now though, with the hope that I'll get around to wrapping this up into a proper module that others may easily import and used.  After all... regex based transform seems like a core capability, and this approach is too convenient to ignore.


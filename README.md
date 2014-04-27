cvs-fast-export
===============

This is a CVSNT-to-Git conversion utility written in Scala. It is quite
immature and can perfectly convert the repositories I designed it for, 
but may not work as well for others.

Use it at your your own risk. You may encounter special cases in your
CVSNT repositories that the utility does not handle correctly yet.  If
you report these to me, I will see what I can do to improve it.

Note that this utility was developed to convert CVSNT repositories -
it is unlikely to produce ideal results with vanilla CVS repositories,
and may not work with them at all.

Options
-------

TBD

Modes of Operation
------------------

TBD

Mapping Author Names
--------------------

TBD

Translation of CVS Keywords
---------------------------

TBD

CVSNT Substitution Modes
------------------------

TBD

Limitations
-----------

TBD

Building
--------

TBD

Disclaimer
----------

This is my first significant project using Scala, and is kind of a
learning ground. In very many cases in this code, I fall back to
imperative programming rather than attempt to find a functional way to
solve a problem. The resulting program works well, but should not be
used a model for how to write Scala code!

Acknowledgements
----------------

cvs-fast-export includes OptionParser by Curt Sellmer.
See https://github.com/sellmerfud/optparse for the original.

License
-------

This software is licensed under the MIT License as detailed in the
included NOTICE file.

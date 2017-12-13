# Taint Analysis using P/Taint

P/Taint is activated using the `--information-flow` flag, is fully
integrated into Doop, and is available for both Souffle and Logicblox
backends. P/Taint can track taint flow out of the box through Android
and Servlet applications. Custom platform architectures can be easily
integrated into P/Taint by creating new lists of taint sources/sinks
and taint transform methods.

In the case of Android, additional sensitive layout controls can be
defined using the `--information-flow-extra-controls` flag.

## TODO
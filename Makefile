# Currently either DaCapo2006 or DaCapoBach
suite      = DaCapo2006
commonArgs = -t 90
analyses   = context-insensitive 1-call-site-sensitive 1-object-sensitive+heap 2-type-sensitive+heap
benchmarks = ../benchmarks
outDir     = .
prefix     = 

.PHONY: run clean fail
fail:
	$(error "Must specify a target")

#-----------------------------#
# DO NOT CHANGE THE FOLLOWING #
#-----------------------------#
DOOP            = ./doop

DaCapo2006_args = --dacapo --tamiflex $(call DaCapo2006_tamiflex,$1)
DaCapo2006_jars = antlr bloat chart eclipse fop hsqldb jython luindex lusearch pmd xalan
DaCapo2006_jar  = $(benchmarks)/dacapo-2006/$(1).jar
DaCapo2006_libs = $(benchmarks)/dacapo-2006/$(1)-deps.jar
DaCapo2006_tamiflex = $(benchmarks)/dacapo-2006/$(1)-tamiflex.log

DaCapoBach_args = --dacapo-bach --tamiflex $(call DaCapoBach_tamiflex,$1)
DaCapoBach_jars = avrora batik eclipse h2 jython luindex lusearch pmd sunflow xalan
DaCapoBach_jar  = $(benchmarks)/dacapo-bach/$(1)/$(1).jar
DaCapoBach_libs = $(benchmarks)/dacapo-bach/$(1)/$(1)-libs
DaCapoBach_tamiflex = $(benchmarks)/dacapo-bach/$(1)/$(1)-tamiflex.log

define benchmarkRun

run.$1.$2:
	$(DOOP) -a $1 -j $(call $(4)_jar,$2) $(call $(4)_libs,$2) $(call $(4)_args,$2) $3 2>&1 | tee $(outDir)/$(prefix)$(2)_$(1).trace

run: run.$1.$2

endef

$(foreach analysis, $(analyses),\
	$(foreach benchmark,$($(suite)_jars),\
		$(eval $(call benchmarkRun,$(analysis),$(benchmark),$(commonArgs),$(suite)))))

clean:
	rm -rf logs/* out/* results/* last-analysis

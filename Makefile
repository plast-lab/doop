# Currently either DaCapo2006 or DaCapoBach
suite      = DaCapo2006
commonArgs = --timeout 90 --ssa --cache
analyses   = context-insensitive 1-object-sensitive+heap 2-type-sensitive+heap
benchmarks = benchmarks
outDir     = .
prefix     = 

.PHONY: run clean clean-full fail
fail:
	$(error "Must specify a target")

#-----------------------------#
# DO NOT CHANGE THE FOLLOWING #
#-----------------------------#
DOOP            = ./doop

DaCapo2006_args = --dacapo
DaCapo2006_jars = antlr bloat chart eclipse fop hsqldb jython luindex lusearch pmd xalan
DaCapo2006_jar  = $(benchmarks)/dacapo-2006/$(1).jar

DaCapoBach_args = --dacapo-bach
DaCapoBach_jars = avrora batik eclipse h2 jython luindex lusearch pmd sunflow xalan
DaCapoBach_jar  = $(benchmarks)/dacapo-bach/$(1)/$(1).jar

define benchmarkRun

run.$1.$2:
	$(DOOP) -a $1 -j $(call $(4)_jar,$2) $(call $(4)_args,$2) $3 2>&1 | tee $(outDir)/$(prefix)$(2)_$(1).trace
	hg identify --id >> $(outDir)/$(prefix)$(2)_$(1).trace

run: run.$1.$2

endef

$(foreach analysis, $(analyses),\
	$(foreach benchmark,$($(suite)_jars),\
		$(eval $(call benchmarkRun,$(analysis),$(benchmark),$(commonArgs),$(suite)))))

clean:
	rm -rf logs/* out/* results/* last-analysis
clean-full: clean
	rm -rf cache/*

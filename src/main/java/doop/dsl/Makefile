javaGen    := BaseListener.java Lexer.java Listener.java Parser.java
tokGen     := .tokens Lexer.tokens
grammar_DL := Datalog
javaGen_DL := $(abspath $(javaGen:%=$(grammar_DL)%))
tokGen_DL  := $(tokGen:%=$(grammar_DL)%)
grammar_Scr:= Script
javaGen_Scr:= $(abspath $(javaGen:%=$(grammar_Scr)%))
tokGen_Scr := $(tokGen:%=$(grammar_Scr)%)

javaSrc := $(abspath Main.java DatalogListenerImpl.java)
classes := $(javaSrc:%.java=%.class)

# Tests
simple  := t1 t2 t3 t4 t5 t6 sample
simple  := $(simple:%=examples/%.logic)

logicDir:= ../../../../../logic
facts   := declarations flow-insensitivity-declarations import entities-import flow-insensitivity-delta
facts   := $(facts:%=$(logicDir)/facts/%.logic)
cfg     := declarations rules tests
cfg     := $(cfg:%=$(logicDir)/addons/cfg-analysis/%.logic)
scc     := SCC-approx spanning
scc     := $(scc:%=$(logicDir)/addons/graph/scc/%.logic)
aux     := declarations delta
aux     := $(aux:%=$(logicDir)/addons/auxiliary-heap-allocations/%.logic)
tami    := declarations delta fact-declarations import post-import rules
tami    := $(tami:%=$(logicDir)/addons/tamiflex/%.logic)
transf  := declarations delta rules
transf  := $(transf:%=$(logicDir)/addons/transform/%.logic)
misc    := addons/type-analysis
misc    := $(misc:%=$(logicDir)/%.logic)

analyses:= $(wildcard ../../../../../out/context-insensitive/antlr/*.logic)
analyses+= $(wildcard ../../../../../out/context-insensitive/avrora/*.logic)

script  := examples/t1.dls

tests   := $(simple) $(facts) $(cfg) $(scc) $(aux) $(tami) $(transf) $(misc) $(analyses) $(script)


.PHONY: all run clean

all: build_DL build_Scr build_Impl

build_DL  : $(javaGen_DL) $(tokGen_DL)
build_Scr : $(javaGen_Scr) $(tokGen_Scr)
build_Impl: $(classes)

$(javaGen_DL) $(tokGen_DL): $(grammar_DL).g4
	java org.antlr.v4.Tool $<
	cd ../../ && javac $(javaGen_DL)

$(javaGen_Scr) $(tokGen_Scr): $(grammar_Scr).g4
	java org.antlr.v4.Tool $<
	cd ../../ && javac $(javaGen_Scr)

$(classes): $(javaSrc)
	cd ../../ && javac $?

run: all
	@./runTest $(tests)

clean:
	rm -rf $(javaGen_DL) $(javaGen_Scr) *.class *.tokens

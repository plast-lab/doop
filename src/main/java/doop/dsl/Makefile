grammar := Datalog
javaGen := BaseListener.java Lexer.java Listener.java Parser.java
javaGen := $(javaGen:%=$(grammar)%)
tokGen  := .tokens Lexer.tokens
tokGen  := $(tokGen:%=$(grammar)%)
javaSrc := Main.java DSLListener.java
classes := $(javaSrc:%.java=%.class)
tests   := t1 t2 t3 t4 t5 t6 sample
tests   := $(tests:%=examples/%.logic)

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

tests   += $(facts) $(cfg) $(scc) $(aux) $(tami) $(transf) $(misc) $(analyses)

.PHONY: all run clean

all: build
	
build: $(javaGen) $(tokGen) #$(classes)

$(javaGen) $(tokGen): $(grammar).g4 
	java org.antlr.v4.Tool $<
	javac $(javaGen)

#$(classes): $(javaSrc)
#	javac $?

run: build
	@runTest $(tests)

clean:
	rm -rf $(javaGen) *.class *.tokens

grammar := Datalog
javaGen := BaseListener.java Lexer.java Listener.java Parser.java
javaGen := $(javaGen:%=$(grammar)%)
tokGen  := .tokens Lexer.tokens
tokGen  := $(tokGen:%=$(grammar)%)
javaSrc := Main.java DSLListener.java
classes := $(javaSrc:%.java=%.class)

.PHONY: all run clean

all: $(javaGen) $(tokGen) $(classes)

$(javaGen) $(tokGen): $(grammar).g4 
	java org.antlr.v4.Tool $<
	javac $(javaGen)

$(classes): $(javaSrc)
	javac $?

run:
	java org.antlr.v4.gui.TestRig LogiQL program -tree

clean:
	rm -rf $(javaGen) *.class *.tokens

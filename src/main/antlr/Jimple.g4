grammar Jimple;

@header {
package org.clyze.jimple;
}

program
	: klass ;


klass
	: modifier* 'class' IDENTIFIER 'extends' IDENTIFIER '{' (field|method)*'}' ;

modifier
	: 'public'
	| 'protected'
	| 'private'
	| 'static'
	| 'abstract'
	;

field
	: modifier* IDENTIFIER IDENTIFIER ';' ;

method
	: modifier* IDENTIFIER IDENTIFIER '(' identifierList? ')' '{' methodBody '}' ;

identifierList
	: IDENTIFIER
	| identifierList ',' IDENTIFIER
	;

methodBody
	: (statement ';' | IDENTIFIER ':')+ ;

statement
	: declarationStmt
	| assignmentStmt
	| returnStmt
	| invokeStmt
	| allocationStmt
	| jumpStmt
	;

declarationStmt
	: IDENTIFIER identifierList ;

assignmentStmt
	: IDENTIFIER ':=' IDENTIFIER ':' IDENTIFIER
	| IDENTIFIER '=' value
	| IDENTIFIER '=' value OP value
	;

returnStmt
	: 'return' IDENTIFIER? ;

invokeStmt
	: ('specialinvoke'|'virtualinvoke') IDENTIFIER '.' methodSig '(' valueList? ')'
	| 'staticinvoke' methodSig '(' valueList? ')'
	;

allocationStmt
	: IDENTIFIER '=' 'new' IDENTIFIER ;

methodSig
	: '<' IDENTIFIER ':' IDENTIFIER IDENTIFIER '(' identifierList* ')' '>' ;

value
	: IDENTIFIER
	| INTEGER
	| REAL
	;

valueList
	: value
	| valueList ',' value
	;

jumpStmt
	: ('if' value ('==' | '!=' | '<' | '<=' | '>' | '>=') value)? 'goto' IDENTIFIER ;


// Lexer

INTEGER
	: [0-9]+
	| '0'[0-7]+
	| '0'[xX][0-9a-fA-F]+
	;

fragment
EXPONENT
	: [eE][-+]?INTEGER ;

REAL
	: INTEGER EXPONENT
	| INTEGER EXPONENT? [fF]
	| (INTEGER)? '.' INTEGER EXPONENT? [fF]?
	;

BOOLEAN
	: 'true' | 'false' ;

STRING
	: '"' ~["]* '"' ;

fragment
IDENTIFIER_BASE
	: [a-zA-Z_][a-zA-Z0-9_]* ;

fragment
IDENTIFIER_SUF
	: '#_' [0-9]+ ;

IDENTIFIER
	: [$@]? IDENTIFIER_BASE ('.' IDENTIFIER_BASE)* IDENTIFIER_SUF? '[]'?
	| '<' IDENTIFIER_BASE '>'
	;

OP
	: '+' | '-' | '*' | '/' ;


WHITE_SPACE
	: [ \t\r\n]+ -> skip ;

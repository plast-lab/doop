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
	: modifier* IDENTIFIER TAG_L? IDENTIFIER TAG_R? '(' identifierList? ')' '{' methodBody '}' ;

identifierList
	: IDENTIFIER
	| identifierList ',' IDENTIFIER
	;

methodBody
	: (statement ';')+ ;

statement
	: declarationStmt
	| assignmentStmt
	| returnStmt
	| invokeStmt
	| allocationStmt
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
	: TAG_L IDENTIFIER ':' IDENTIFIER TAG_L? IDENTIFIER TAG_R? '(' identifierList* ')' TAG_R ;

value
	: IDENTIFIER
	| INTEGER
	| REAL
	;

valueList
	: value
	| valueList ',' value
	;


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
	: [$@]? IDENTIFIER_BASE ('.' IDENTIFIER_BASE)* IDENTIFIER_SUF? '[]'? ;

TAG_L
	: '<' ;

TAG_R
	: '>' ;

OP
	: '+' | '-' | '*' | '/' ;


WHITE_SPACE
	: [ \t\r\n]+ -> skip ;

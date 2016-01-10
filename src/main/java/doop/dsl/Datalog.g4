grammar Datalog;

@header {
//package doop.dsl;
} 

program
	: (declaration | constraint | rule_ | directive)* ;

declaration
	: predicate '->' predicateList? '.'
	| predicateName '(' IDENTIFIER ')' ',' refmode '->' primitiveType '.'
	;

constraint
	: ruleBody '->' ruleBody '.' ;

rule_
	: predicateList ('<-' ruleBody?)? '.'
	| predicate '<-' aggregation ruleBody '.'
	;

directive
	: predicateName '(' '`' predicateName ')' '.'
	| predicateName '[' ('`' predicateName)? ']' '=' primitiveConstant '.'
	;

predicate
	: (ADD | RM)? predicateName ('@' LB_STAGE)? '(' parameterList? ')'
	| (ADD | RM | UP)? functionalHead '=' parameter
	| (ADD | RM)? refmode
	| primitiveType
	;

ruleBody
	: predicate
	| comparison
	| ruleBody ',' ruleBody
	| ruleBody ';' ruleBody
	| '(' ruleBody ')'
	| '!' ruleBody
	;

aggregation
	: 'agg' '<<' IDENTIFIER '=' predicate '>>' ;

refmode
	: predicateName ('@' LB_STAGE)? '(' IDENTIFIER ':' parameter ')' ;

functionalHead
	: predicateName ('@' LB_STAGE)? '[' parameterList? ']' ;

predicateName
	: '$'? IDENTIFIER
	| predicateName ':' IDENTIFIER
	;

primitiveType
	: IDENTIFIER CAPACITY? '(' IDENTIFIER ')' ;
/*
	: ( 'int' ('[' ('32' | '64') ']')?
	  | 'uint' ('[' ('32' | '64') ']')?
	  | 'float' ('[' ('32' | '64') ']')?
	  | 'decimal' ('[' ('64' | '128') ']')?
	  | 'boolean'
	  | 'string'
	  ) '(' IDENTIFIER ')'
	;
*/

primitiveConstant
	: INTEGER
	| FLOAT
	| BOOLEAN
	| STRING
	;

parameter
	: IDENTIFIER
	| functionalHead
	| primitiveConstant
	| expr
	;

comparison
	: expr ('=' | '<' | '<=' | '>' | '>=' | '!=') expr ;

expr
	: expr ('+' | '-' | '*' | '/') expr
	| '(' expr ')'
	| IDENTIFIER
	| functionalHead
	| primitiveConstant
	;

predicateList
	: predicate
	| predicateList ',' predicate
	;

parameterList
	: parameter
	| parameterList ',' parameter
	;



// Lexer

LB_STAGE
	: 'init'
	| 'initial'
	| 'prev'
	| 'previous'
	;

CAPACITY
	: '[' ('32' | '64' | '128') ']' ;

ADD
	: '+' ;
RM
	: '-' ;
UP
	: '^' ;

INTEGER
	: [0-9]+
	| '0'[0-7]+
	| '0'[xX][0-9a-fA-F]+
	;

fragment
EXPONENT
	: [eE][-+]?INTEGER ;

FLOAT
	: INTEGER EXPONENT
	| INTEGER EXPONENT? [fF]
	| (INTEGER)? '.' INTEGER EXPONENT? [fF]?
	;

BOOLEAN
	: 'true' | 'false' ;

STRING
	: '"' ~["]* '"' ; 

IDENTIFIER
	: [?]?[a-zA-Z_][a-zA-Z_0-9]* ;


LINE_COMMENT
	: '//' ~[\r\n]* -> skip ;

BLOCK_COMMENT
	: '/*' .*? '*/' -> skip ;

WHITE_SPACE
	: [ \t\r\n]+ -> skip ;

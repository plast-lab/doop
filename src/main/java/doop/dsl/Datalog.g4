grammar Datalog;

program
	: (declaration | rule_)* ;

declaration
	: predicate '->' predicateList? '.' ;

rule_
	: predicateList '<-' ruleBody? '.' ;

predicate
	: IDENTIFIER '(' variableList? ')'
	| functionalHead '=' variable
	| ( 'int'   '[' ('32' | '64') ']' 
	  | 'float' '[' ('32' | '64') ']'
	  | 'boolean'
	  | 'string'
	  ) '(' IDENTIFIER ')'
	;

ruleBody
	: predicate
	| comparison
	| ruleBody ',' ruleBody
	| ruleBody ';' ruleBody
	| '(' ruleBody ')'
	| '!' ruleBody
	;


functionalHead
	: IDENTIFIER '[' variableList? ']' ;

comparison
	: expr COMPARISON_OP__NOT_EQ expr
	| expr '=' expr
	;

expr
	: expr ARITHMETIC_OP expr
	| INTEGER
	| STRING
	| IDENTIFIER
	| functionalHead
	| '(' expr ')'
	;

predicateList
	: predicate
	| predicateList ',' predicate
	;

variable
	: IDENTIFIER
	| INTEGER
	| STRING
	;

variableList
	: variable
	| variableList ',' variable
	;



// Lexer

COMPARISON_OP__NOT_EQ
	: '<' | '<=' | '>' | '>=' | '!=' ;

ARITHMETIC_OP
	: '+' | '-' | '*' | '/' ;

INTEGER
	: [0-9]+ ;

STRING
	: '"' ~["]* '"' ; 

IDENTIFIER
	: '?'? [a-zA-Z_][a-zA-Z0-9_:]* ;

LINE_COMMENT
	: '//' ~[\r\n]* -> skip ;

BLOCK_COMMENT
	: '/*' .*? '*/' -> skip ;

WHITE_SPACE
	: [ \t\r\n]+ -> skip ;

grammar Datalog;

program
	: (declaration | rule_ | directive)* ;

declaration
	: predicate '->' predicateList? '.'
	| predicateName '(' IDENTIFIER ')' ',' refmode '->' primitiveType '.'
	;

rule_
	: predicateList ('<-' ruleBody?)? '.' ;

directive
	: predicateName '(' '`' predicateName ')' '.'
	| predicateName '[' ('`' predicateName)? ']' '=' (INTEGER | BOOLEAN | STRING) '.'
	;

predicate
	: ('+' | '-')? predicateName ('@' LB_STAGE)? '(' parameterList? ')'
	| ('+' | '-' | '^')? functionalHead '=' parameter
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


functionalHead
	: predicateName '[' parameterList? ']' ;

refmode
	: predicateName '(' IDENTIFIER ':' IDENTIFIER ')' ;

primitiveType
	: ( 'int' ('[' ('32' | '64') ']')?
	  | 'uint' ('[' ('32' | '64') ']')?
	  | 'float' ('[' ('32' | '64') ']')?
	  | 'decimal' ('[' ('64' | '128') ']')?
	  | 'boolean'
	  | 'string'
	  ) '(' IDENTIFIER ')'
	;

comparison
	: expr COMPARISON_OP__NOT_EQ expr
	| expr '=' expr
	;

expr
	: expr ('+' | '-' | '*' | '/') expr
	| INTEGER
	| REAL
	| BOOLEAN
	| STRING
	| IDENTIFIER
	| functionalHead
	| '(' expr ')'
	;

predicateList
	: predicate
	| predicateList ',' predicate
	;

predicateName
	: IDENTIFIER
	| predicateName ':' IDENTIFIER
	;

parameter
	: IDENTIFIER
	| INTEGER
	| REAL
	| BOOLEAN
	| STRING
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

COMPARISON_OP__NOT_EQ
	: '<' | '<=' | '>' | '>=' | '!=' ;

//ARITHMETIC_OP
//	: '+' | '-' | '*' | '/' ;

INTEGER
	: [0-9]+ ;

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

IDENTIFIER
	: [?]?[a-zA-Z_][a-zA-Z_0-9]* ;


LINE_COMMENT
	: '//' ~[\r\n]* -> skip ;

BLOCK_COMMENT
	: '/*' .*? '*/' -> skip ;

WHITE_SPACE
	: [ \t\r\n]+ -> skip ;

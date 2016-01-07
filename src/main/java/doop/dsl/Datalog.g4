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
	| predicateName '[' ('`' predicateName)? ']' '=' primitiveConstant '.'
	;

predicate
	: (ADD | RM)? predicateName ('@' LB_STAGE)? '(' parameterList? ')'
	| (ADD | RM | UP)? functionalHead '=' parameter
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


refmode
	: predicateName '(' IDENTIFIER ':' IDENTIFIER ')' ;

functionalHead
	: predicateName '[' parameterList? ']' ;

predicateName
	: IDENTIFIER
	| predicateName ':' IDENTIFIER
	;

primitiveType
	: ( 'int' ('[' ('32' | '64') ']')?
	  | 'uint' ('[' ('32' | '64') ']')?
	  | 'float' ('[' ('32' | '64') ']')?
	  | 'decimal' ('[' ('64' | '128') ']')?
	  | 'boolean'
	  | 'string'
	  ) '(' IDENTIFIER ')'
	;

primitiveConstant
	: INTEGER
	| FLOAT
	| BOOLEAN
	| STRING
	;

parameter
	: IDENTIFIER
	| primitiveConstant
	;

comparison
	: expr ('=' | '<' | '<=' | '>' | '>=' | '!=') expr ;

expr
	: expr ('+' | '-' | '*' | '/') expr
	| '(' expr ')'
	| parameter
	| functionalHead
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

ADD
	: '+' ;
RM
	: '-' ;
UP
	: '^' ;

INTEGER
	: [0-9]+ ;

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

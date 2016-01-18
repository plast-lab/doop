grammar Script;

@header {
package doop.dsl.script;
} 

program
	: topLevelPrimitive* ;

topLevelPrimitive
	: defineStage
	| defineCommand
	| applyStage
	| carryStage
	| finalize_
	| deltaLogic
	;


defineStage
	: STAGE IDENTIFIER stageBlock ; 

stageBlock
	: '{' stagePrimitive+ '}' ;

stagePrimitive
	: ECHO STRING
	| START_TIME
	| END_TIME
	| LOGIC filepath
	;

defineCommand
	: COMMAND IDENTIFIER commandBlock ;

commandBlock
	: '{' commandPrimitive+ '}' ;

commandPrimitive
	: EXPORT (IDENTIFIER (AS IDENTIFIER)?)?
	| IMPORT (IDENTIFIER (AS IDENTIFIER)?)?
	| CMD
	| defineVariable
	;

defineVariable
	: IDENTIFIER '=' filepath
	| IDENTIFIER '=' STRING
	;

applyStage
	: APPLY stageBlock
	| APPLY commandBlock
	| APPLY IDENTIFIER
	;

carryStage
	: CARRY ;

finalize_
	: FINALIZE ;

deltaLogic
	: DELTA filepath ;

filepath
	: IDENTIFIER
	| COMPLEX_FILEPATH
	;



// Lexer

STAGE
	: 'stage' ;
APPLY
	: 'apply' ;
CARRY
	: 'carry' ;
FINALIZE
	: 'finalize' ;
DELTA
	: 'delta' ;
COMMAND
	: 'command' ;
ECHO
	: 'echo' ;
LOGIC
	: 'logic' ;
START_TIME
	: 'startTime' ;
END_TIME
	: 'endTime' ;
EXPORT
	: 'export' ;
IMPORT
	: 'import' ;
AS
	: 'as' ;

CMD
	: '`' ~[`]* '`' ;

STRING
	: '"' ~["]* '"' ;

fragment
IDENTIFIER_BASE
	: [a-zA-Z_\-0-9.]+ ;

IDENTIFIER
	: IDENTIFIER_BASE ;

COMPLEX_FILEPATH
	: IDENTIFIER_BASE ('/' IDENTIFIER_BASE)+ '/'? ;


LINE_COMMENT
	: '//' ~[\r\n]* -> skip ;

BLOCK_COMMENT
	: '/*' .*? '*/' -> skip ;

WHITE_SPACE
	: [ \t\r\n]+ -> skip ;

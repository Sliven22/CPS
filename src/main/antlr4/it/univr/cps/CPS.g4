/*
 * CPS — C-Like Pseudocode Script, linguaggio imperativo tipizzato staticamente.
 * Laboratorio di Linguaggi, A.A. 2025/2026.
 *
 * Funzionalita' avanzate implementate:
 *   - Funzioni (livello medio): sezione dichiarativa, ricorsione, parametri per valore e per riferimento
 *   - Strutture Dati: array a dimensione variabile, anche multidimensionali
 *   - Zucchero Sintattico: ++/--, assegnamenti composti, ternario, interpolazione di stringhe
 *   - Valutazione Pigra: assegnamenti 'lazy' con cattura per valore
 */
grammar CPS;

// ---------------------------------------------------------------- struttura

// Le dichiarazioni di funzione sono dichiarative e possono comparire anche dopo
// alcune istruzioni (come negli script CPS); non vengono mai eseguite.
program : com EOF ;

funDecl : FUNCTION ID LPAR params? RPAR ARROW retType COLON com? END ;

retType : type | VOID ;

params : param (COMMA param)* ;

// 'ref' seleziona il passaggio per riferimento; senza, il passaggio e' per valore.
param : REF? type ID ;

type : ARRAY+ TYPE
     | TYPE
     ;

// ---------------------------------------------------------------- comandi
//
// Un comando e' una sequenza di comandi elementari, che si dividono in due specie:
//
//   - i comandi SEMPLICI (assegnamenti, print, return, ...) vanno separati dal ';';
//   - i comandi strutturati usano ':' per introdurre il corpo; tutti terminano con 'end',
//     tranne 'do', che termina con 'while (condizione);'.
//
// Il ';' e' un separatore, non un terminatore: l'ultimo comando di una sequenza non lo richiede.
// Un ';' superfluo e' tollerato dopo un comando qualunque (anche prima di 'end'), ma
// solo uno: ';;' resta un errore, perche' in CPS il comando che non fa niente si scrive 'nop'.
com : funDecl com?
    | simpleCom (SEMICOLON com?)?
    | closedCom SEMICOLON? com?
    | doWhileCom com?
    ;

simpleCom : type ID dimensions* (ASSIGN exp)?                     # decl
          | LAZY type ID ASSIGN exp                               # lazyDecl
          | lvalue ASSIGN exp                                     # assign
          | lvalue op=(ADD_A | SUB_A | MUL_A | DIV_A | MOD_A) exp # compoundAssign
          | lvalue op=(INCR | DECR)                               # postCrementCom
          | op=(INCR | DECR) lvalue                               # preCrementCom
          | THROW exp                                             # throw
          | RETURN exp?                                           # return
          | PRINT exp                                             # print
          | ID LPAR args? RPAR                                    # callCom
          | NOP                                                   # nop
          ;

closedCom : IF condition COLON com?
            (ELSE IF condition COLON com?)*
            (ELSE COLON com?)? END                                 # ifChain
          | WHILE condition DO COLON com? END                      # while
          | TRY COLON com? CATCH LPAR ID RPAR COLON com? END        # tryCatch
          | FOR type ID IN exp RANGE exp DO COLON com? END          # forRange
          | FOR type ID IN exp DO COLON com? END                    # forEach
          | FOR LPAR type ID COMMA type ID RPAR IN exp DO COLON com? END # forDestructuring
          ;

doWhileCom : DO COLON com? WHILE condition SEMICOLON                # doWhile
           ;

condition : LPAR exp RPAR
          | exp
          ;

dimensions : LBRACK exp RBRACK ;

// Bersaglio assegnabile: una variabile, oppure una cella di array.
lvalue : ID                         # varLvalue
       | lvalue LBRACK exp RBRACK   # cellLvalue
       ;

// ---------------------------------------------------------------- espressioni
//
// L'ordine delle alternative fissa la precedenza: piu' in alto, piu' stretto il legame.

exp : num                                           # numeric
    | BOOL                                          # boolean
    | CHAR                                          # character
    | STRING                                        # string
    | LBRACK args? RBRACK                           # arrayLit
    | NEW TYPE (LBRACK exp RBRACK)+                 # arrayNew
    | LPAR exp RPAR                                 # parExp
    | ID LPAR args? RPAR                            # call
    | LEN LPAR exp RPAR                             # len
    | TOSTR LPAR exp RPAR                           # toStr
    | exp LBRACK exp RBRACK                         # index
    | <assoc=right> exp POW exp                     # pow
    | lvalue op=(INCR | DECR)                       # postCrement
    | op=(INCR | DECR) lvalue                       # preCrement
    | LPAR TYPE RPAR exp                            # cast
    | NOT exp                                       # not
    | SUB exp                                       # neg
    | exp op=(MUL | DIV | MOD) exp                  # mulDivMod
    | exp op=(ADD | SUB) exp                        # addSub
    | exp op=(LT | LEQ | GEQ | GT) exp              # cmpExp
    | exp op=(EQQ | EQUALS | NEQ) exp               # eqExp
    | exp AND exp                                   # and
    | exp OR exp                                    # or
    | <assoc=right> exp QUESTION exp COLON exp      # ternary
    | ID                                            # id
    ;

args : exp (COMMA exp)* ;

num : INT   # intNum
    | REAL_LITERAL # realNum
    ;

// ---------------------------------------------------------------- lessico

// operatori composti e crementi: token piu' lunghi, quindi vincono sul match massimale
ADD_A : '+=' ;
SUB_A : '-=' ;
MUL_A : '*=' ;
DIV_A : '/=' ;
MOD_A : '%=' ;
INCR  : '++' ;
DECR  : '--' ;

ADD : '+' ;
SUB : '-' ;
MUL : '*' ;
DIV : '/' ;
MOD : '%' ;
POW : '^' ;

RANGE : '..' ;

EQQ : '==' ;
EQUALS : 'equals' ;
NEQ : '!=' ;
LEQ : '<=' ;
GEQ : '>=' ;
LT  : '<'  ;
GT  : '>'  ;
NOT : '!'  ;
AND : '&&' | 'and' ;
OR  : '||' | 'or' ;

QUESTION : '?' ;
COLON    : ':' ;

TOSTR : 'toStr' ;
LEN   : 'len'   ;
NEW   : 'new'   ;

FUNCTION : 'function' ;
ARROW    : '->' ;
ARRAY    : 'array' ;
IN       : 'in' ;
DO       : 'do' ;
END      : 'end' ;
FOR      : 'for' ;

IF     : 'if'     ;
ELSE   : 'else'   ;
WHILE  : 'while'  ;
TRY    : 'try'    ;
CATCH  : 'catch'  ;
THROW  : 'throw'  ;
RETURN : 'return' ;
LAZY   : 'lazy'   ;
REF    : 'ref'    ;
VOID   : 'void'   ;
ASSIGN : '='      ;
PRINT  : 'print'  ;
NOP    : 'nop'    ;

LPAR      : '(' ;
RPAR      : ')' ;
LBRACK    : '[' ;
RBRACK    : ']' ;
SEMICOLON : ';' ;
COMMA     : ',' ;

BOOL : 'true' | 'false' ;
TYPE : 'int' | 'real' | 'char' | 'bool' | 'string' ;

// I letterali numerici sono senza segno: il meno unario e' un operatore (regola 'neg'),
// cosi' '3-2' si lessicalizza come 3, -, 2 e non come 3, -2.
INT : '0' | POSDIGIT DIGIT* ;
REAL_LITERAL : INT '.' DIGIT+ ;

CHAR   : '\'' (~['\\\r\n] | ESC) '\'' ;
STRING : '"' STRCHR* '"' ;

fragment DIGIT    : [0-9] ;
fragment POSDIGIT : [1-9] ;
fragment STRCHR   : ~["\\\r\n] | ESC ;
fragment ESC      : '\\' [btnfr"'\\$] ;

ID : [a-zA-Z_] [a-zA-Z_0-9]* ;

LINE_COMMENT  : '//' ~[\r\n]*   -> skip ;
BLOCK_COMMENT : '/*' .*? '*/'   -> skip ;
WS            : [ \t\r\n]+      -> skip ;

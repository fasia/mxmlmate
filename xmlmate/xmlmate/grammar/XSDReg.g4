grammar XSDReg;
@header{
package regex;
}

regExp             :    branch ( Pipe branch )* ;
branch             :   	piece*;
piece              :   	atom quantifier?;

quantifier	   :   	QMark # Optional
					|Star # KleeneStar
					|Plus # KleenePlus
					|( LBrace quantity RBrace ) # ComplexQuantity;
quantity	   :   	quantRange|quantMin|quantExact;
quantRange	   :   	quantExact Comma quantExact;
quantMin	   :   	quantExact Comma;
quantExact         :   	Digit+;
Digit		   :	[0-9];

atom               :  normalChar # NormCharAtom
					| charClass # CharClassAtom
					| ( LPar regExp RPar ) # PrioOverrideAtom;
normalChar	   :   	~(Dot|BSL|QMark|Star|Plus|LBrace|RBrace|LPar|RPar|Pipe|LBrack|RBrack|IsBlock);

charClass	   :   	charClassExpr|charClassEsc|singleCharEsc|wildcard;
wildcard	:	Dot;
charClassExpr	   :   	LBrack charGroup RBrack;
charGroup	   :   	posCharGroup|negCharGroup ( Minus charClassExpr )?;
negCharGroup	   :   	Caret posCharGroup;
posCharGroup	   :    (charRange|charClassEsc)+;
                       
charRange	   :   	seRange | xmlCharIncDash;
seRange            :    charOrEsc Minus charOrEsc;
charOrEsc          :    xmlChar | singleCharEsc;
xmlChar            :    ~(BSL|Minus|LBrack|RBrack|IsBlock);
// there is a corner case with the ^
xmlCharIncDash     :    ~(Caret|BSL|LBrack|RBrack|IsBlock)|singleCharEsc;

singleCharEsc	   :    newLn|ret|tab|simpleCharEscape;
newLn              :    BSL LETTER_n;
ret                :    BSL LETTER_r;
tab                :    BSL LETTER_t;
simpleCharEscape	:	BSL (BSL|Pipe|Dot|QMark|Star|Plus|LPar|RPar|LBrace|RBrace|Minus|LBrack|RBrack|Caret);

charClassEsc	   :   	singleCharEsc|multiCharEsc|catEsc|complEsc;
catEsc             :   	smallP charProp RBrace;
complEsc	   :    bigP charProp RBrace;
smallP             :    BSL LETTER_p LBrace;
bigP               :    BSL LETTER_P LBrace;
charProp	   :   	isCategory|IsBlock; // todo make IsBlock a parser rule

isCategory         :   	cat_L|cat_Lu|cat_Ll|cat_Lt|cat_Lm|cat_Lo|cat_M|cat_Mn|cat_Mc|cat_Me|cat_N|cat_Nd|cat_Nl|cat_No|cat_P|cat_Pc|cat_Pd|cat_Ps|cat_Pe|cat_Pi|cat_Pf|cat_Po|cat_Z|cat_Zs|cat_Zl|cat_Zp|cat_S|cat_Sm|cat_Sc|cat_Sk|cat_So|cat_C|cat_Cc|cat_Cf|cat_Co|cat_Cn;

cat_L   :LETTER_L;              //'L';	//All Letters
cat_Lu	:LETTER_L LETTER_u;     //'Lu';	//uppercase
cat_Ll	:LETTER_L LETTER_l;	//'Ll';	//lowercase
cat_Lt	:LETTER_L LETTER_t;	//'Lt';	//titlecase
cat_Lm	:LETTER_L LETTER_m;	//'Lm';	//modifier
cat_Lo	:LETTER_L LETTER_o;	//'Lo';	//other
cat_M	:LETTER_M;		//'M';	//All Marks
cat_Mn	:LETTER_M LETTER_n;	//'Mn';	//nonspacing
cat_Mc	:LETTER_M LETTER_c;	//'Mc';	//spacing combining
cat_Me	:LETTER_M LETTER_e;	//'Me';	//enclosing
cat_N	:LETTER_N;		//'N';	//All Numbers
cat_Nd	:LETTER_N LETTER_d;	//'Nd';	//decimal digit
cat_Nl	:LETTER_N LETTER_l;	//'Nl';	//letter
cat_No	:LETTER_N LETTER_o;	//'No';	//other
cat_P	:LETTER_P;		//'P';	//All Punctuation
cat_Pc	:LETTER_P LETTER_c;	//'Pc';	//connector
cat_Pd	:LETTER_P LETTER_d;	//'Pd';	//dash
cat_Ps	:LETTER_P LETTER_s;	//'Ps';	//open
cat_Pe	:LETTER_P LETTER_e;	//'Pe';	//close
cat_Pi	:LETTER_P LETTER_i;	//'Pi';	//initial quote (may behave like Ps or Pe depending on usage)
cat_Pf	:LETTER_P LETTER_f;	//'Pf';	//final quote (may behave like Ps or Pe depending on usage)
cat_Po	:LETTER_P LETTER_o;	//'Po';	//other
cat_Z	:LETTER_Z;		//'Z';	//All Separators
cat_Zs	:LETTER_Z LETTER_s;	//'Zs';	//space
cat_Zl	:LETTER_Z LETTER_l;	//'Zl';	//line
cat_Zp	:LETTER_Z LETTER_p;	//'Zp';	//paragraph
cat_S	:LETTER_S;		//'S';	//All Symbols
cat_Sm	:LETTER_S LETTER_m;	//'Sm';	//math
cat_Sc	:LETTER_S LETTER_c;	//'Sc';	//currency
cat_Sk	:LETTER_S LETTER_k;	//'Sk';	//modifier
cat_So	:LETTER_S LETTER_o;	//'So';	//other
cat_C	:LETTER_C;		//'C';	//All Others
cat_Cc	:LETTER_C LETTER_c;	//'Cc';	//control
cat_Cf	:LETTER_C LETTER_f;	//'Cf';	//format
cat_Co	:LETTER_C LETTER_o;	//'Co';	//private use
cat_Cn	:LETTER_C LETTER_n;	//'Cn';	//not assigned

IsBlock            :   	BasicLatin |Latin1Supplement |LatinExtendedA |LatinExtendedB |IPAExtensions |SpacingModifierLetters |CombiningDiacriticalMarks |Greek |Cyrillic |Armenian |Hebrew |Arabic |Syriac |Thaana |Devanagari |Bengali |Gurmukhi |Gujarati |Oriya |Tamil |Telugu |Kannada |Malayalam |Sinhala |Thai |Lao |Tibetan |Myanmar |Georgian |HangulJamo |Ethiopic |Cherokee |UnifiedCanadianAboriginalSyllabics |Ogham |Runic |Khmer |Mongolian |LatinExtendedAdditional |GreekExtended |GeneralPunctuation |SuperscriptsandSubscripts |CurrencySymbols |CombiningMarksforSymbols |LetterlikeSymbols |NumberForms |Arrows |MathematicalOperators |MiscellaneousTechnical |ControlPictures |OpticalCharacterRecognition |EnclosedAlphanumerics |BoxDrawing |BlockElements |GeometricShapes |MiscellaneousSymbols |Dingbats |BraillePatterns |CJKRadicalsSupplement |KangxiRadicals |IdeographicDescriptionCharacters |CJKSymbolsandPunctuation |Hiragana |Katakana |Bopomofo |HangulCompatibilityJamo |Kanbun |BopomofoExtended |EnclosedCJKLettersandMonths |CJKCompatibility |CJKUnifiedIdeographsExtensionA |CJKUnifiedIdeographs |YiSyllables |YiRadicals |HangulSyllables |PrivateUse |CJKCompatibilityIdeographs |AlphabeticPresentationForms |ArabicPresentationFormsA |CombiningHalfMarks |CJKCompatibilityForms |SmallFormVariants |ArabicPresentationFormsB |Specials |HalfwidthandFullwidthForms ;
fragment BasicLatin	:	'IsBasicLatin';	//[\u0000-\u007F]
fragment Latin1Supplement	:	'IsLatin-1Supplement';	//[\u0080-\u00FF]
fragment LatinExtendedA	:	'IsLatinExtended-A';	//[\u0100-\u017F]
fragment LatinExtendedB	:	'IsLatinExtended-B';	//[\u0180-\u024F]
fragment IPAExtensions	:	'IsIPAExtensions';	//[\u0250-\u02AF]
fragment SpacingModifierLetters	:	'IsSpacingModifierLetters';	//[\u02B0-\u02FF]
fragment CombiningDiacriticalMarks	:	'IsCombiningDiacriticalMarks';	//[\u0300-\u036F]
fragment Greek	:	'IsGreek';	//[\u0370-\u03FF]
fragment Cyrillic	:	'IsCyrillic';	//[\u0400-\u04FF]
fragment Armenian	:	'IsArmenian';	//[\u0530-\u058F]
fragment Hebrew	:	'IsHebrew';	//[\u0590-\u05FF]
fragment Arabic	:	'IsArabic';	//[\u0600-\u06FF]
fragment Syriac	:	'IsSyriac';	//[\u0700-\u074F]
fragment Thaana	:	'IsThaana';	//[\u0780-\u07BF]
fragment Devanagari	:	'IsDevanagari';	//[\u0900-\u097F]
fragment Bengali	:	'IsBengali';	//[\u0980-\u09FF]
fragment Gurmukhi	:	'IsGurmukhi';	//[\u0A00-\u0A7F]
fragment Gujarati	:	'IsGujarati';	//[\u0A80-\u0AFF]
fragment Oriya	:	'IsOriya';	//[\u0B00-\u0B7F]
fragment Tamil	:	'IsTamil';	//[\u0B80-\u0BFF]
fragment Telugu	:	'IsTelugu';	//[\u0C00-\u0C7F]
fragment Kannada	:	'IsKannada';	//[\u0C80-\u0CFF]
fragment Malayalam	:	'IsMalayalam';	//[\u0D00-\u0D7F]
fragment Sinhala	:	'IsSinhala';	//[\u0D80-\u0DFF]
fragment Thai	:	'IsThai';	//[\u0E00-\u0E7F]
fragment Lao	:	'IsLao';	//[\u0E80-\u0EFF]
fragment Tibetan	:	'IsTibetan';	//[\u0F00-\u0FFF]
fragment Myanmar	:	'IsMyanmar';	//[\u1000-\u109F]
fragment Georgian	:	'IsGeorgian';	//[\u10A0-\u10FF]
fragment HangulJamo	:	'IsHangulJamo';	//[\u1100-\u11FF]
fragment Ethiopic	:	'IsEthiopic';	//[\u1200-\u137F]
fragment Cherokee	:	'IsCherokee';	//[\u13A0-\u13FF]
fragment UnifiedCanadianAboriginalSyllabics	:	'IsUnifiedCanadianAboriginalSyllabics';	//[\u1400-\u167F]
fragment Ogham	:	'IsOgham';	//[\u1680-\u169F]
fragment Runic	:	'IsRunic';	//[\u16A0-\u16FF]
fragment Khmer	:	'IsKhmer';	//[\u1780-\u17FF]
fragment Mongolian	:	'IsMongolian';	//[\u1800-\u18AF]
fragment LatinExtendedAdditional	:	'IsLatinExtendedAdditional';	//[\u1E00-\u1EFF]
fragment GreekExtended	:	'IsGreekExtended';	//[\u1F00-\u1FFF]
fragment GeneralPunctuation	:	'IsGeneralPunctuation';	//[\u2000-\u206F]
fragment SuperscriptsandSubscripts	:	'IsSuperscriptsandSubscripts';	//[\u2070-\u209F]
fragment CurrencySymbols	:	'IsCurrencySymbols';	//[\u20A0-\u20CF]
fragment CombiningMarksforSymbols	:	'IsCombiningMarksforSymbols';	//[\u20D0-\u20FF]
fragment LetterlikeSymbols	:	'IsLetterlikeSymbols';	//[\u2100-\u214F]
fragment NumberForms	:	'IsNumberForms';	//[\u2150-\u218F]
fragment Arrows	:	'IsArrows';	//[\u2190-\u21FF]
fragment MathematicalOperators	:	'IsMathematicalOperators';	//[\u2200-\u22FF]
fragment MiscellaneousTechnical	:	'IsMiscellaneousTechnical';	//[\u2300-\u23FF]
fragment ControlPictures	:	'IsControlPictures';	//[\u2400-\u243F]
fragment OpticalCharacterRecognition	:	'IsOpticalCharacterRecognition';	//[\u2440-\u245F]
fragment EnclosedAlphanumerics	:	'IsEnclosedAlphanumerics';	//[\u2460-\u24FF]
fragment BoxDrawing	:	'IsBoxDrawing';	//[\u2500-\u257F]
fragment BlockElements	:	'IsBlockElements';	//[\u2580-\u259F]
fragment GeometricShapes	:	'IsGeometricShapes';	//[\u25A0-\u25FF]
fragment MiscellaneousSymbols	:	'IsMiscellaneousSymbols';	//[\u2600-\u26FF]
fragment Dingbats	:	'IsDingbats';	//[\u2700-\u27BF]
fragment BraillePatterns	:	'IsBraillePatterns';	//[\u2800-\u28FF]
fragment CJKRadicalsSupplement	:	'IsCJKRadicalsSupplement';	//[\u2E80-\u2EFF]
fragment KangxiRadicals	:	'IsKangxiRadicals';	//[\u2F00-\u2FDF]
fragment IdeographicDescriptionCharacters	:	'IsIdeographicDescriptionCharacters';	//[\u2FF0-\u2FFF]
fragment CJKSymbolsandPunctuation	:	'IsCJKSymbolsandPunctuation';	//[\u3000-\u303F]
fragment Hiragana	:	'IsHiragana';	//[\u3040-\u309F]
fragment Katakana	:	'IsKatakana';	//[\u30A0-\u30FF]
fragment Bopomofo	:	'IsBopomofo';	//[\u3100-\u312F]
fragment HangulCompatibilityJamo	:	'IsHangulCompatibilityJamo';	//[\u3130-\u318F]
fragment Kanbun	:	'IsKanbun';	//[\u3190-\u319F]
fragment BopomofoExtended	:	'IsBopomofoExtended';	//[\u31A0-\u31BF]
fragment EnclosedCJKLettersandMonths	:	'IsEnclosedCJKLettersandMonths';	//[\u3200-\u32FF]
fragment CJKCompatibility	:	'IsCJKCompatibility';	//[\u3300-\u33FF]
fragment CJKUnifiedIdeographsExtensionA	:	'IsCJKUnifiedIdeographsExtensionA';	//[\u3400-\u4DB5]
fragment CJKUnifiedIdeographs	:	'IsCJKUnifiedIdeographs';	//[\u4E00-\u9FFF]
fragment YiSyllables	:	'IsYiSyllables';	//[\uA000-\uA48F]
fragment YiRadicals	:	'IsYiRadicals';	//[\uA490-\uA4CF]
fragment HangulSyllables	:	'IsHangulSyllables';	//[\uAC00-\uD7A3]
fragment PrivateUse	:	'IsPrivateUse';	//[\uE000-\uF8FF]
fragment CJKCompatibilityIdeographs	:	'IsCJKCompatibilityIdeographs';	//[\uF900-\uFAFF]
fragment AlphabeticPresentationForms	:	'IsAlphabeticPresentationForms';	//[\uFB00-\uFB4F]
fragment ArabicPresentationFormsA	:	'IsArabicPresentationForms-A';	//[\uFB50-\uFDFF]
fragment CombiningHalfMarks	:	'IsCombiningHalfMarks';	//[\uFE20-\uFE2F]
fragment CJKCompatibilityForms	:	'IsCJKCompatibilityForms';	//[\uFE30-\uFE4F]
fragment SmallFormVariants	:	'IsSmallFormVariants';	//[\uFE50-\uFE6F]
fragment ArabicPresentationFormsB	:	'IsArabicPresentationForms-B';	//[\uFE70-\uFEFE]
fragment Specials	:	'IsSpecials';	//[\uFEFF-\uFEFF]
fragment HalfwidthandFullwidthForms	:	'IsHalfwidthandFullwidthForms';	//[\uFF00-\uFFEF]

multiCharEsc	   :   space|notSpace|init|nonInit|nameChar|nonNameChar|multiCharEscNumbers|nonMultiCharEscNumbers|printable|nonPrintable;

space              :    BSL LETTER_s;      //[ \t\n\r]
notSpace           :    BSL LETTER_S;      //[^\s]
init               :    BSL LETTER_i;      // the set of initial name characters, those matched by Letter |'_' |':'
nonInit            :    BSL LETTER_I;      //[^\i]
nameChar           :    BSL LETTER_c;      //the set of name characters, those matched by NameChar
nonNameChar        :    BSL LETTER_C;      //[^\c]
multiCharEscNumbers:    BSL LETTER_d;      //\p{Nd}
nonMultiCharEscNumbers: BSL LETTER_D;       //[^\d]
printable          :    BSL LETTER_w;      //[#x0000-#x10FFFF]-[\p{P}\p{Z}\p{C}] (all characters except the set of "punctuation", "separator" and "other" characters)
nonPrintable       :    BSL LETTER_W;      //[^\w]

Dot                :   	'.';
LBrace             :    '{';
RBrace             :    '}';
Pipe               :    '|';
Caret              :    '^';
BSL                :    '\\';
QMark              :    '?';
Star               :    '*';
Plus               :    '+';
Minus              :    '-';
LBrack             :    '[';
RBrack             :    ']';
LPar               :    '(';
RPar               :    ')';
Comma              :    ',';

LETTER_L           :    'L';
LETTER_u           :    'u';
LETTER_l           :    'l';
LETTER_t           :    't';
LETTER_m           :    'm';
LETTER_o           :    'o';
LETTER_M           :    'M';
LETTER_n           :    'n';
LETTER_c           :    'c';
LETTER_e           :    'e';
LETTER_N           :    'N';
LETTER_d           :    'd';
LETTER_P           :    'P';
LETTER_s           :    's';
LETTER_i           :    'i';
LETTER_f           :    'f';
LETTER_Z           :    'Z';
LETTER_p           :    'p';
LETTER_S           :    'S';
LETTER_k           :    'k';
LETTER_C           :    'C';
LETTER_r           :    'r';
LETTER_I           :    'I';
LETTER_D           :    'D';
LETTER_w           :    'w';
LETTER_W           :    'W';

Anything           :    .;
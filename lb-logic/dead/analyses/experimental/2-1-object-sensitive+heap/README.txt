This analysis is just a test of what will happen if I take
2-full-object-sensitive+heap and make both contexts be copies of the
same (the allocation site of the receiver object). The result is, as
expected, that the analysis is semantically exactly equivalent to
1-obj+H but slightly more expensive time-wise (and presumably
space-wise, though I didn't confirm this by observation).

I tried the same thing at the binding site of the context of a newly
allocated object and made 2-full-obj+H be exactly identical to just
1-obj, again with a slight performance cost.

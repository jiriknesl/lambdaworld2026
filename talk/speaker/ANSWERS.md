# Optional answers for Lambda World

These are answers for discussion, not extra passages to read during the talk.

## Why use a finite difference gradient

It lets the cost remain an ordinary numerical function. Each of 72 coordinates uses two nearby
cost evaluations at the same old vector. This is expensive compared with an analytic gradient or
automatic differentiation, and step size affects numerical error. It makes the function interface
visible without introducing another library. No Hessian or symbolic algebra is used.

## Does stopping prove the plan is optimal

No. Small successive movements can result from a small learning rate or numerical effects.
The example uses a linear loss, a convex squared positive-part penalty, and box constraints;
that useful structure does not make every implementation or stopping test a certificate.
The deck reports the run and its stopping reason, not a universal guarantee for gradient descent.

## Does the bird penalty enforce the target

No. A finite penalty trades risk against electricity, so the final risk can exceed twelve.
Clamping enforces the separate zero-to-one setting bounds. The later controller validation checks
another property entirely: its response to detection events. It does not verify the optimized schedule.

## Why a two-week supply cycle

It makes inventory alone insufficient to choose an order. Add the supply phase to the state and
the transition rules become fixed: a regular week always leads to a tight week and vice versa.
The Bellman HOF itself is unchanged. Demand is the stated discrete mixture, independent between
weeks and identical in both phases. Real correlated demand or uncertain prices would need more state
or a different transition model.

## Why do the Bellman bars change the recommended order

Starting at zero, the nth exact backup gives the optimal n-week discounted value. A greedy action
scored using that table considers the current week plus those n future weeks. The slide explicitly
skips from update one to three when the preferred order changes. The next slide replays every update.
The final policy is stationary on the augmented state; its twenty-week evaluation is not a claim
of finite-horizon terminal-week optimality.

## What does the residual bound mean

For the exact discounted finite-state operator over real-valued tables, with nonempty feasible
actions and normalized nonnegative probabilities, let V star be its fixed point. If the residual
is delta = max absolute difference between V and B(V), then the value error is at most
 delta / (1 − gamma). At gamma 0.9, a residual of 0.001 gives a 0.01 bound. This follows from
contraction and the usual completeness argument. The recorded Rainbow receipt is for the additive
closeness theorem; it does not itself certify existence, this whole corollary, or floating-point
rounding in the demo. An approximate value bound also does not automatically identify an exactly
optimal action when action scores are close.

## What exactly was exhaustively checked

The finite controller model includes known and unknown contexts, detection levels zero through
two, three representative draw bands, timer positions, action and a bounded waiting counter.
Detection before a timer boundary is retained as a state before the reset. Exploration starts from
all encoded initial states and stops only on set closure. The checked property is absence of
confident detection with RUN in those states. Sensor errors, physical stopping delay and events
outside this model are not covered. This is executable model exploration, separate from Rainbow.

## Can the computation run in parallel

The gradient coordinates can be estimated independently from one old vector. Bellman can compute
each next-state value independently from the same old table. A parallel map can exploit that
independence if its overhead is worthwhile. The dependency between successive iterate states
remains sequential. This deck does not demonstrate or measure a parallel implementation; ClojureScript
in this browser is not silently using JVM pmap.

## Do types or proofs remove bugs

Types rule out certain mistakes, depending on what they express. A proof checks a particular
statement under a particular semantics and assumptions. Neither the implementation language nor
the existence of one theorem certifies the whole application. The composition example is checked
from its exact Rainbow term; the Bellman receipt concerns an exact mathematical operator. There
is no proved correspondence to the browser program.

## If time is short

Keep the explanations that introduce state, score, the Bellman update and reachability. Shorten
visual holds on slides 14, 16 and 20, using End to show the final numerical state. On slide 21, read
the stock-one comparison and the empty-tank example without reading every row. On slide 15, state
the measured outcome without reading the whole implementation. Never compensate by reading the
proof faster. The timing file gives estimates at 110, 125 and 140 words per minute; Jiri’s aloud
rehearsal determines the actual pace.

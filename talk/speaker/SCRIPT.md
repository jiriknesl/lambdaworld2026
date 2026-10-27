# Lambda World speaker script

3,226 spoken words. Planning pace: 125 words/minute, plus 03:01 of pauses and visual holds. Speech and holds: 28:49; with 02:00 for questions: 30:49. This is an estimate, not an aloud rehearsal.

Speak the prose; square-bracketed stage cues in exports are not spoken. Long numerical traces have one continuous passage plus a final-state passage. No Clojure or OR prerequisite; explain notation at first use. Timing is a calculation, not an aloud rehearsal.

Use Right for a reveal and Down for the next slide. Space plays a numerical trace; End jumps to its final state. The N presenter window shows the matching speech. Keep the release open before the talk so its numerical runs have finished.

Every rendered phase has a passage. Ranges such as “2–penultimate” cover continuous animation frames: speak that passage once over the range, not once per frame.

## 01 Rebuilding Operations Research in Higher-Order Functions

00:00–00:12 · 19 words · 3s visual holds

### Phase 1

[Let the title settle.]

Hello, I’m Jiri. We’ll build three operations research examples with higher-order functions, then examine a proof about an update.

## 02 About me

00:12–00:42 · 55 words · 3s visual holds

### Phase 1

[Introduce your background briefly; the interviews line links to the Flexiana channel.]

I started programming thirty-four years ago, and I’ve been using Clojure for fourteen years. My education is in economics. Thirteen years ago, I started the Barcelona Meetup Group. I also interview many Clojure open-source authors on Flexiana’s YouTube channel. This talk brings together my interests in functional programming, mathematical models, and decisions we can explain.

## 03 Why Clojure?

00:42–01:09 · 51 words · 3s visual holds

### Phase 1

[Move down the four reasons.]

I chose Clojure for small functions, immutable data, and REPL-oriented programming. Keeping old states makes experiments easy to compare. Code is also data, which we’ll use later when we get to proofs. You don’t need Clojure experience today. I’ll introduce its notation where it matters; the functional ideas should be familiar.

## 04 Operations research

01:09–01:47 · 72 words · 3s visual holds

### Phase 1

[Leave the concrete examples for later.]

Operations research uses models to compare choices under objectives and constraints. Optimization searches for a plan. Decision-making under uncertainty compares possible outcomes.

I also include validation: checking whether a system respects its specification. Reachability usually belongs to verification; it matters here because a profitable decision system can still violate a requirement. These are different questions. We’ll use a different kind of state for each, but the same way of building an iteration.

## 05 iterate takes an update function

01:47–02:56 · 128 words · 8s visual holds

### Phase 1

[Point to fn, then the function passed to iterate. The first state is the starting guess.]

Here is a square-root update. In Clojure, a call puts the function first. Defn names a function, and fn creates a function. Newton-sqrt takes the number whose root we want and returns an update for a positive guess. That update averages the guess with the number divided by the guess. We pass it to iterate. The first element is our starting value, one.

### Phase 2

[Right once. Follow the complete chain; avoid reading every decimal.]

Each result becomes the input to the next call. One becomes one point five, then about one point four one seven. The outer function chooses the problem; iterate handles the repetition. Both are higher-order functions: one returns a function, the other accepts it. Take requests five states from this lazy sequence. Five is just a display limit. We still need to decide when to stop.

## 06 Choose when to stop

02:56–04:09 · 137 words · 7s visual holds

### Phase 1

[Connect the fixed-point equation to square root of two, then compare the final two values.]

A fixed point is a value the update leaves unchanged. For this particular update, that means x squared equals two. We usually cannot represent that answer exactly, so we stop when successive values are close enough. Here the change is below ten to the minus twelve. Close-enough is another function supplied to our helper. The question mark is part of its name. A small change is not automatically a bound on the error.

### Phase 2

[Right once. Show the helper, pointing only to iterate and converged?.]

Here is the helper we will reuse. It applies the step with iterate and checks successive pairs using the supplied stopping test. It returns the states so we can draw them. The sequence is lazy: the work cap does not force a thousand updates when we converge earlier. That is the interface we need for the examples: an update function and a stopping test.

## 07 Plan tomorrow’s turbine settings

04:09–04:34 · 46 words · 3s visual holds

### Phase 1

[Point to the forecast and the turbine scene.]

Our first problem is a wind farm. Six turbines, twelve hourly decisions each: seventy-two settings to choose. Wind sets the available electricity; we choose how much to retain. At noon, this example offers eighty-four kilowatt-hours per turbine. Setting one retains it all; one half retains forty-two.

## 08 What makes one plan better?

04:34–05:48 · 141 words · 6s visual holds

### Phase 1

[Read the three whole-plan rows.]

If electricity were our only concern, we would retain everything. Stopping turbines gives up electricity. It also reduces the exposure of birds passing the farm. We need to say how we compare those two goals. The cost function on the left adds up electricity lost across the whole plan. Clojure maps can be called as lookup functions: run applied to a turbine-hour key returns that setting. For produces the contributions; reduce plus adds them.

### Phase 2

[Right. Point to T2 and T3 at noon.]

The turbines are not equally exposed. Each cell here is exposure at full operation for a turbine and an hour. T3 is close to the flight path; T2 is farther away. The hourly traffic pattern is shared, and location scales it. So curtailing T3 at noon removes much more exposure for the same electricity loss. This is an illustrative exposure index, not a prediction of birds killed.

## 09 Optimization is iterate

05:48–07:30 · 194 words · 9s visual holds

### Phase 1

[Point to the slope on the graph, then the returned fn.]

Suppose we score only electricity lost. The graph shows how that cost changes when one setting changes. A downhill direction reduces the cost. Gradient takes our cost function and returns a function that estimates the slopes at a supplied plan. It does that numerically, without symbolic differentiation.

### Phase 2

[Right. Read one row, then gesture across all six.]

Descent-step subtracts a small multiple of each slope. Half minus point zero zero zero one times minus eighty-four gives point five zero eight four. The argument destructuring extracts x from the state map. Assoc returns the updated map without changing the old one. Hash-parenthesis is a shorter way to write a lambda; percent denotes its argument. All seventy-two coordinates use the same old vector. The noon settings move equally here because their electricity forecasts are equal.

### Phase 3

[Right. Follow solver-step from the inside outward.]

Clamped takes that update function and returns another function. It calls the update, then clips every proposed setting to zero through one. The solver is now a function from a plan state to a new plan state. Iterate feeds each new state back. All the problem-specific work is inside the step we supplied. Before we change the cost, let’s look at the small multiplier that controls how far each update moves.

## 10 How far should one update move?

07:30–08:00 · 51 words · 6s visual holds

### Phase 1

[Return to the electricity-only slope at turbine 1, noon.]

The rate we just supplied controls the movement. Keep turbine one at noon, with slope minus eighty-four. At this rate, half becomes point five zero eight four.

### Phase 2

[Right. Point to the changed rate and result.]

Double the rate and this proposed movement doubles. For this electricity-only cost, running everything is obviously best. Now let’s supply the tradeoff with birds.

## 11 Which plan should win?

08:00–09:49 · 209 words · 9s visual holds

### Phase 1

[Point to A and B, then the electricity row.]

Now let’s choose the tradeoff. Plan A retains half everywhere. Plan B is identical except that T3 stops at noon. B gives up another forty-two kilowatt-hours. On electricity alone, A wins.

### Phase 2

[Right. Explain the small risk-14 example before the table.]

I choose a target of twelve for total exposure. Above that, I add one hundred times the squared excess to the cost. A risk of fourteen is two above target, so it adds four hundred. Twelve and one hundred are modelling choices. The weight converts squared exposure into score units comparable with the electricity-loss term.

Now B wins: its reduction in exposure is worth more under this chosen score than the extra electricity lost. Both candidate plans still exceed the target; we have compared a direction of improvement, not finished the optimization. This is a soft penalty. It encourages lower exposure; it does not guarantee a strict limit.

### Phase 3

[Right. Point to cost, risk and the returned fn.]

Here is the wrapper. It receives the original cost, a risk function, and the parameters, then returns a new cost function. Comp in the definition composes functions right to left: first turn the vector into a plan, then measure its risk. Both old and new costs accept the same vector and return one number. Gradient can sample either one. We change the objective without changing the update algorithm or iterate.

## 12 Same updates, different objectives

09:49–11:17 · 150 words · 16s visual holds

### Phase 1

[Explain the grid before pressing Space.]

Both sides start with the same plan, rate, and stopping test. A row is one turbine; a column is an hour; darker means more generation retained. The only difference is the supplied cost function.

### Phase 2–penultimate

[Space to play; pause near the middle if useful. Speak over the changing grids. This paragraph covers every intermediate numerical phase; do not narrate each number.]

On the left, electricity alone pushes the settings upward. On the right, the bird penalty changes which settings are worth increasing or reducing. The plan changes most where traffic and exposure are high. These frames are successive drafts of tomorrow’s plan. The day itself is not passing yet.

### Phase last

[End. Read the displayed final generation and risk, without memorizing them.]

Compare the final generation and exposure. The penalty buys a reduction in exposure by giving up some generation. Here both traces stop because the largest setting change is below point zero zero one. The cap is six hundred updates. That says the numerical search has settled under our tolerance; it is not a general proof of optimality. Could a different update reach a settled plan in fewer steps?

## 13 Can momentum reduce the number of updates?

11:17–12:03 · 83 words · 6s visual holds

### Phase 1

[Point to the returned fn and the velocity in state.]

We have just watched plain gradient descent on the bird-aware cost. Here is a different update for that same problem: momentum. It calls the base step and adds ninety percent of the previous movement. That velocity travels in the immutable state. Iterate still receives a state-to-state function.

### Phase 2

[Right. Follow the wrappers inside out; read the measured table.]

The base step performs descent; momentum wraps it; clamped wraps the result. Clamping clips position but keeps velocity. We have changed the solver’s update without changing the model. Now let’s replay the plain solver’s resulting day.

## 14 Play the resulting day

12:03–13:01 · 91 words · 14s visual holds

### Phase 1

[Watch the highlighted bird reach the rotor, flash red and fall.]

Now we play a day through a fixed schedule. The red rectangle marks the current hour. First, every turbine runs. Watch the highlighted bird hit a moving rotor and fall. This is an illustrative strike; our cost model measures exposure, not fatalities.

### Phase 2

[Right. Watch the same crossing during busy hours when the exposed turbine is stopped.]

With the penalty, the chosen schedule stops this exposed turbine during busy hours. The same illustrated bird now passes through. As activity drops in the evening, the birds disappear from this view. The clock is advancing; the plan is no longer changing. The optimizer produced the schedule being replayed.

## 15 Buy hydrogen before the customers arrive

13:01–15:42 · 304 words · 15s visual holds

### Phase 1

[Show the real port trial, then move to the illustrative station.]

This is a hydrogen-powered terminal tractor at the Port of Algeciras. EVO began trials with Logístika 360 in April 2026. That gives us a practical question: how much hydrogen should a station buy before it knows how many customers will arrive? I’ll use a small model to explain the decision. The tank size, prices and demand probabilities that follow are illustrative; they are not data from the port.

### Phase 2

[Point to tank capacity, supplier table, then customer probabilities.]

For decision-making, consider a hydrogen station. It has room for four units, and each customer truck takes one. The station buys from a supplier before it knows how many customers will arrive. Regular weeks offer up to three units at a price of two. Tight weeks offer only one unit, at a price of six. Those weeks alternate. The tank’s physical capacity stays four; the supplier’s available quantity changes.

### Phase 3

[Right. Follow the delivery into the tank.]

Customer arrivals are uncertain: forty percent of weeks have no trucks, fifteen percent have one, and forty-five percent have a convoy of three. The average is one and a half, but the full distribution matters. We use the same distribution each week, independently.

Here we buy one unit in a regular week, bringing our stock to two. The order is chosen before we see which customer outcome happens.

### Phase 4

[Right. Point to the turned-away truck.]

A convoy arrives. We sell to two trucks and turn the third away. This is one possible outcome, not the demand we knew when choosing the order.

### Phase 5

[Right. Point first to reward, then next-state.]

We sell each unit for ten, so this outcome earns twenty minus the purchase cost of two: eighteen. Unsold units would cost one each to hold for the week. The next state contains leftover stock and the next supply window. Our objective is expected profit over repeated weeks, multiplying each following week’s contribution by another factor of point nine. This is a modelling preference for earlier returns, not a ten-percent hydrogen leak.

## 16 Buy for this week—or the next?

15:42–16:14 · 61 words · 3s visual holds

### Phase 1

[Read the state and compare the two order columns.]

The state is a pair: supply week and stock. Orders returns feasible purchase quantities.

In a regular week with one unit already in stock, buying two covers the largest possible convoy this week. Buying three fills the tank to four. Why carry more than any current convoy needs? Look at the leftovers: either way, the next week has expensive, limited supply.

## 17 Future values change the order

16:14–19:44 · 406 words · 15s visual holds

### Phase 1

[Keep the state fixed throughout all four reveals.]

V is a table: for each supply week and stock level, it estimates profit from then onward. Start all entries at zero. Clojure maps are functions too, so V of a state is a lookup in that table. For each possible order, average over the three demand outcomes: this week’s profit plus point nine times the value of the next state. With every future value zero, only this week matters. Order two wins.

### Phase 2

[Right. Point to the unchanged best order.]

After one update, the table contains optimal one-week values. Using those as the future term now scores a current order plus one more week. We score the same orders again, from the same initial situation. Order two still looks best. Nothing has been bought; this is calculation.

### Phase 3

[Right. Identify the jump to update three.]

We skip update two on this slide. After three updates, V accounts for three weeks; scoring an order with it looks one week further ahead. Now order three wins. The extra stock has value because it carries us into the tight supply week. We did not add an if statement saying to buy more before a tight week. The recommendation follows from the transitions, costs, and future values.

### Phase 4

[Identify q as expected-return. Read the order scores, then distinguish the three-week and four-week horizons.]

Here, q is the model’s expected-return function. Give it V, a state and a proposed order. It averages this week’s profit plus the discounted value of next week’s leftover stock over the possible demands.

For regular week, stock one, the four orders score 19.10, 23.64, 28.04 and 28.35. We use max to select 28.35, the highest score. Bellman repeats that calculation for all ten states—two supply weeks times five stock levels—and returns a new value table.

The old entry was 23.06. It covered three weeks from this state. The new entry covers four: this week plus the three-week values of possible next states. That increase is not a measured saving from changing the order. No hydrogen has been bought; we are extending the planning horizon.

### Phase 5

[Keep the same V. Contrast the winning score, 28.35, with the winning purchase quantity, 3.]

Now we use the same input V and the same scores, but keep the winning order instead. Max-key returns three because ordering three scored 28.35. Three means units to buy, not profit or target stock. We already hold one unit, so buying three fills the tank to four.

Best-order does this for every state and returns a map from state to order—a policy. Clojure maps support function calls, so the expression below looks up this state and returns three. It neither updates V nor uses the new value table from the preceding reveal.

## 18 Hydrogen: iterate the value table

19:44–21:18 · 166 words · 14s visual holds

### Phase 1

[Identify the axes and colours before starting the animation.]

Now iterate updates the whole table of profit estimates. Each pair of bars is a starting stock level: blue for a regular week, amber for a tight week. Height is money, not hydrogen or an order. The separate best-order call produces the order column. Both use the same model.

### Phase 2–penultimate

[Space. Follow the stock-1 row; allow the animation to finish. This paragraph covers every intermediate numerical phase; do not narrate each number.]

Every update revises all ten values using the old table. Best-order then compares possible purchases using those values. Watch stock one: the recommendation changes as the estimates account for more future consequences. No purchases happen during this animation; it is planning.

### Phase last

[End. Point to the residual and the two stock-1 recommendations.]

The largest change is now below our tolerance. In the stopping lambda, percent-one and percent-two stand for the two successive tables. With one unit in stock, the recommended action depends on the week: buy three when supply is cheap, and buy none in the tight week. For a fixed finite model and discount below one, exact value iteration has a convergence argument. Later I’ll show the bound behind it; here we are watching a floating-point calculation.

## 19 The best order depends on the supply window

21:18–22:38 · 154 words · 6s visual holds

### Phase 1

[Read stock 1 and the comparison, then point to the empty-tank tight-week recommendation.]

The policy maps supply window and stock to a purchase quantity. “This week only” maximizes immediate expected profit, with no value assigned to leftover stock. Bellman also accounts for future weeks. We evaluate every rule over the same twenty discounted weeks, weighting all demand branches. Filling toward three is already good; Bellman does a little better. This is a comparison of stationary policies, not an optimization for a business closing after week twenty.

There is a less obvious recommendation here. With an empty tank in a tight week, buy one, even though that purchase has negative expected profit this week. The cost is paid now. If nobody arrives, the unit carries into next week, when cheap supply is capped. That future benefit outweighs the immediate expected loss in this model. Looking only at immediate expected profit misses that value. This comes from the actual model; it is not a general claim about hydrogen markets.

## 20 Stop when a bird is detected

22:38–24:08 · 163 words · 12s visual holds

### Phase 1

[Let the bird cross the detection line. Point to RUN and the failure count.]

Back at the wind farm, suppose a bird is detected while the turbine is running. The controller should issue a stop command. Watch this one: it receives the detection but keeps running. Its decision table has no entry for this situation. The number below comes from checking every reachable state in our small model. So this is a failure found by the check, not just a worrying animation.

### Phase 2

[Right. Let the same bird cross the line; point to STOP, then read the wrapper.]

Now wrap the decision function with a shield. If there is a detection, return stop. Otherwise, call the original decision function. We call this wrapped function as soon as a detection arrives. The animation uses that actual output. The same exhaustive check now finds no state where a confident detection leaves the command at run. Underneath, iterate keeps adding possible next states until the set stops growing. This validates the response in this finite software model. It does not tell us whether a sensor missed a bird, or how quickly a real turbine can brake.

## 21 iterate underneath all three

24:08–24:50 · 81 words · 3s visual holds

### Phase 1

[Read across each row; do not repeat the whole story.]

Underneath all three examples is iterate. The state was a vector of turbine settings, then a value table. For validation, it was the set of states the controller could reach. Each example supplies its own update and stopping test. This is a useful way to build and inspect algorithms. It does not give them identical guarantees: a finite set stops growing, while small numerical changes need more care. We also changed the optimizer’s pace and added momentum by working with functions.

## 22 Rainbow

24:50–26:17 · 175 words · 3s visual holds

### Phase 1

[Introduce Rainbow, then state the Lean cross-check and the conditions of the search benchmark. Point to the composition proof; explain f then g without reading every syntax form.]

Rainbow is a theorem prover and code generator focused on Clojure and operations research. I am designing it for AI-assisted proving, aiming for more useful verified progress per token. We reuse checked lemmas and let local tools do routine checking.

We also cross-check with Lean. All 1,721 theorems in our default checkpoint were accepted with exact types, and Lean reports the same axioms for every one. That independently checks these exported proofs.

On a held-out benchmark of 200 theorems from our own corpus, Rainbow solved 171 and Lean’s core automation solved 131. Each had thirty seconds per theorem, with no AI help. Lean ran without Mathlib, using the best of four setups. These are results on our corpus, not a claim that Rainbow beats Lean in general. Here is a small proof using composition. If f takes a proof of P to a proof of Q, and g takes Q to R, applying g to f of p gives a proof of R. Rainbow checked this term without axioms. The slide shows that recorded check.

## 23 Plans for future Rainbow

26:17–27:08 · 99 words · 4s visual holds

### Phase 1

[Explain the two directions. Distinguish the working explorer from the planned OR-specific views.]

I want to take Rainbow further in two directions. First, code generation: support more operations research algorithms, build them from reusable higher-order functions, and inspect and run the generated Clojure in the REPL. Today this covers a modeled subset of the language.

Second, proof visualization. We already have a Proof Explorer where we can follow theorem dependencies, see the assumptions, open proof steps and inspect sample calculations. The next step is to make this useful for operations research: show which constraints limit a solution and how a bound establishes its quality. Those OR-specific views are still to be built.

## 24 Where we use Rainbow now?

27:08–28:12 · 125 words · 4s visual holds

### Phase 1

[Tell the business story; distinguish the modeled saving from the decision that has a proof.]

We are using this work on a model of an e-SAF plant in the Bay of Algeciras. e-SAF is synthetic aviation fuel. We use real hourly electricity prices from OMIE and local wind and solar history. Our model plans how to run the electrolyzers, storage tanks, carbon dioxide supply and fuel production. It helps us choose the plant’s size, when to buy electricity and how to run it day to day. Against a plant designed for an average year, our model estimates about 7.9 million euros less cost annually. That is a modeled comparison, not a promise to an operating factory. For fuel blending, Rainbow checks that our chosen mix is the best one allowed by the model. The annual saving is still an estimate.

## 25 Applied Higher-Order Functions

28:12–28:38 · 47 words · 3s visual holds

### Phase 1

[Gesture to the book photograph; leave the URL visible.]

I wrote Applied Higher-Order Functions about using higher-order functions in operations research. But the ideas apply more broadly, especially if you design algorithms that optimize or help make decisions. The book has longer explanations and runnable examples. You can find it at flexiana dot com slash book.

## 26 Thank you

28:38–28:49 · 18 words · 3s visual holds

### Phase 1

[Leave the contact details visible for questions.]

Thank you. You can reach me at jiri at flexiana dot com. I’d be happy to take questions.

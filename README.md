# Series Block

Android app that declines an incoming call at once when the caller's number is in a number series
that you save. It also keeps a short call log that shows how many seconds each incoming call rang.

## Why

Some spam dialers move to the next number in their block every day (…24, then …25, then …26).
A block on one number never stops them. Each new number also has few spam reports, so caller-ID
apps let the call ring.

## Rules

Write one rule on each line, and start numbers with +91 or 0:

| Rule | Blocks |
|---|---|
| `+91140` | 10,000,000 numbers (3-digit prefix): every number that starts with +91 140 |
| `+9114012345` | 100 numbers (8-digit prefix): +911401234500 to +911401234599 |
| `+911401234500 100` | 100 numbers, from +911401234500 to +911401234599 |
| `+911401234590 20` | 20 numbers, from …590 to …609. A length can cross a prefix boundary. |
| `+911401234500` | That one number |

The app shows each rule with the count of numbers that it blocks. For a prefix, it also shows the
number of digits after +91, for example "+9114012345…  (100 numbers, 8-digit prefix)".

- "+", "+9" and "+91" are ignored, because they would block every Indian number.
- The first number of a series with a length must be a full +91 number.

## Ring-time log

For each incoming call, the app adds one line: how many seconds the call rang and how it ended
(answered, missed, declined, or declined by Series Block). The ring time counts from when the call
arrived, which is also the time in the system call log. Android's own call log keeps only the talk
time, so it shows 0 s for every missed or declined call.

## Setup

1. Build: `./build.sh`. It needs Android SDK build-tools 35, platform 36 and a JDK, but no Gradle.
   The output is `build/SeriesBlock.apk`.
2. Install: `adb install -r build/SeriesBlock.apk`
3. Allow call access, one time: `adb shell appops set com.smart.seriesblock MANAGE_ONGOING_CALLS allow`
4. Open Series Block, type the rules and tap Save.

To set the rules from adb (separate rules with `;`):

```
adb shell "am start -n com.smart.seriesblock/.MainActivity --es rules '+91140;+911401234500 100' --ez on true"
```

Tests: `./test.sh` runs the number-matching tests with a plain JDK.

## How it works

Telecom binds a non-UI `InCallService` because the app holds the `MANAGE_ONGOING_CALLS` app-op.
When an incoming call rings and its number matches a rule, the service calls `Call.reject()`, the
same action as a tap on Decline. The network treats a declined call as busy, so if "forward when
busy" is set, the call still goes to that number (for example, voicemail).

The app does not need the dialer role or the call-screening role, so it works next to the default
dialer and a caller-ID app such as Truecaller.

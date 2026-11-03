#!/usr/bin/env bash
#
# Benchmarks Doop's Datalog engines against each other -- Souffle versus FlowLog
# (--Xvia-flowlog) -- over a DaCapo corpus: for every bench, job count, engine
# and repetition it runs one ./doop analysis, records the solve and compile
# times Doop reports and the engine's peak RSS, and prints a median summary
# table (speedup and memory cost of FlowLog over Souffle per bench and job
# count). Two flag profiles are built in: main-method (closed program, --main)
# and open-program (--open-programs jackee).
#
# Usage: bin/bench-engines.sh [options]      (run from anywhere; see --help)
#
# Inputs it needs, each settable by a flag or an environment variable:
#   DOOP_BENCH_ARTIFACT  --artifact      the corpus: <dir>/dacapo/<bench>/inputs/{i,l}/*.jar
#                                        and <dir>/java_platforms/JREs (DOOP_PLATFORMS_LIB)
#   FLOWLOG_HOME         --flowlog-home  directory holding the flowlog-compiler binary
#                                        (only when the flowlog engine is selected)
#   JAVA_HOME            --jdk-home      the JDK Doop runs with (17+)
#   CARGO_HOME           --cargo-root    cargo's home, for the FlowLog Rust build
#   DOOP_HOME            --doop-home     defaults to the checkout this script lives in
#
# Results go under $DOOP_HOME/out/engine-bench/<timestamp>/ (or --rundir):
# env.txt (provenance, including the Doop revision), timings.tsv (one row per
# run), <id>.out (the full ./doop output of each run) and summary.txt. Each run's
# own out/<id>/ directory stays in place; its database is dropped unless
# --database keep is given, which you need in order to diff relations between
# the two engines afterwards.

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ANALYSIS="context-insensitive"
PLATFORM="java_8"
BENCHES="avrora sunflow zxing graphchi pmd fop spring batik"
ENGINES="souffle flowlog"
JOBS="4 8 16 32"
STATS="full"
REPS="3"
TIMEOUT="1440"
DATABASE="drop"
DOOP_HOME="${DOOP_HOME:-$(cd "$HERE/.." && pwd)}"
ARTIFACT="${DOOP_BENCH_ARTIFACT:-}"
FLOWLOG_HOME="${FLOWLOG_HOME:-}"
JDK_HOME="${JAVA_HOME:-}"
CARGO_ROOT="${CARGO_HOME:-$HOME/.cargo}"
RUNDIR=""
PROFILE="main-method"

declare -A FLAGS_MAIN_METHOD=(
  [avrora]="--main avrora.Main --regex 'avrora.*:cck.*:jintgen.*'"
  [sunflow]="--main org.sunflow.Benchmark --regex 'org.sunflow.*'"
  [zxing]="--main com.google.zxing.client.j2se.CommandLineRunner --regex 'com.google.zxing.*'"
  [graphchi]="--main edu.cmu.graphchi.apps.ALSMatrixFactorization --regex 'edu.cmu.graphchi.*'"
  [pmd]="--main net.sourceforge.pmd.PMD --regex 'net.sourceforge.pmd.*'"
  [fop]="--main org.apache.fop.cli.Main --regex 'org.apache.fop.*'"
  [spring]="--main org.springframework.samples.petclinic.PetClinicApplication --regex 'org.springframework.*'"
  [batik]="--main org.apache.batik.apps.rasterizer.Main --regex 'org.apache.batik.*'"
)

declare -A FLAGS_OPEN_PROGRAM=(
  [avrora]="--open-programs jackee --regex 'avrora.*:cck.*:jintgen.*'"
  [sunflow]="--open-programs jackee --regex 'org.sunflow.*'"
  [zxing]="--open-programs jackee --regex 'com.google.zxing.*'"
  [graphchi]="--open-programs jackee --regex 'edu.cmu.graphchi.*'"
  [pmd]="--open-programs jackee --regex 'net.sourceforge.pmd.*'"
  [fop]="--open-programs jackee --regex 'org.apache.fop.*'"
  [spring]="--open-programs jackee --regex 'org.springframework.*'"
  [batik]="--open-programs jackee --regex 'org.apache.batik.*'"
)

PROFILES="main-method open-program"

profile_flags() {
  case "$PROFILE" in
    main-method)  printf '%s' "${FLAGS_MAIN_METHOD[$1]-}" ;;
    open-program) printf '%s' "${FLAGS_OPEN_PROGRAM[$1]-}" ;;
  esac
}

profile_has() {
  case "$PROFILE" in
    main-method)  [ -v "FLAGS_MAIN_METHOD[$1]" ] ;;
    open-program) [ -v "FLAGS_OPEN_PROGRAM[$1]" ] ;;
    *)            return 1 ;;
  esac
}

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

  --analysis NAME           $ANALYSIS
  --platform NAME           $PLATFORM
  --benches "A B C"         $BENCHES
  --engines "A B"           $ENGINES
  --jobs "N N N"            $JOBS
  --stats none|default|full $STATS
  --reps N                  $REPS
  --timeout MINUTES         $TIMEOUT
  --database keep|drop      $DATABASE
  --doop-home DIR           $DOOP_HOME   (env DOOP_HOME; default: this checkout)
  --artifact DIR            ${ARTIFACT:-<unset>}   (env DOOP_BENCH_ARTIFACT; required)
  --flowlog-home DIR        ${FLOWLOG_HOME:-<unset>}   (env FLOWLOG_HOME; required for the flowlog engine)
  --jdk-home DIR            ${JDK_HOME:-<unset>}   (env JAVA_HOME; required)
  --cargo-root DIR          $CARGO_ROOT   (env CARGO_HOME)
  --profile NAME            $PROFILE   (main-method | open-program)
  --rundir DIR              \$DOOP_HOME/out/engine-bench/<timestamp>
  -h, --help
EOF
}

die() { echo "error: $*" >&2; exit 2; }
need() { [ -n "${2:-}" ] || die "$1 requires a value"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --*=*) set -- "${1%%=*}" "${1#*=}" "${@:2}"; continue ;;
    --analysis)         need "$1" "${2:-}"; ANALYSIS="$2"; shift 2 ;;
    --platform)         need "$1" "${2:-}"; PLATFORM="$2"; shift 2 ;;
    --benches)          need "$1" "${2:-}"; BENCHES="${2//,/ }"; shift 2 ;;
    --engines)          need "$1" "${2:-}"; ENGINES="${2//,/ }"; shift 2 ;;
    --jobs)             need "$1" "${2:-}"; JOBS="${2//,/ }"; shift 2 ;;
    --stats)            need "$1" "${2:-}"; STATS="$2"; shift 2 ;;
    --reps)             need "$1" "${2:-}"; REPS="$2"; shift 2 ;;
    --timeout)          need "$1" "${2:-}"; TIMEOUT="$2"; shift 2 ;;
    --database)         need "$1" "${2:-}"; DATABASE="$2"; shift 2 ;;
    --doop-home)        need "$1" "${2:-}"; DOOP_HOME="$2"; shift 2 ;;
    --artifact)         need "$1" "${2:-}"; ARTIFACT="$2"; shift 2 ;;
    --flowlog-home)     need "$1" "${2:-}"; FLOWLOG_HOME="$2"; shift 2 ;;
    --jdk-home)         need "$1" "${2:-}"; JDK_HOME="$2"; shift 2 ;;
    --cargo-root)       need "$1" "${2:-}"; CARGO_ROOT="$2"; shift 2 ;;
    --profile)          need "$1" "${2:-}"; PROFILE="$2"; shift 2 ;;
    --rundir)           need "$1" "${2:-}"; RUNDIR="$2"; shift 2 ;;
    -h|--help)          usage; exit 0 ;;
    *)                  usage >&2; die "unknown argument: $1" ;;
  esac
done

case "$STATS" in none|default|full) ;; *) die "--stats must be none|default|full" ;; esac
case "$DATABASE" in keep|drop) ;; *) die "--database must be keep or drop" ;; esac
case " $PROFILES " in *" $PROFILE "*) ;; *) die "--profile must be one of: $PROFILES" ;; esac
[ -n "$JOBS" ] || die "--jobs must not be empty"
for j in $JOBS; do
  printf '%s' "$j" | grep -qE '^[1-9][0-9]*$' || die "--jobs values must be positive integers: $j"
done
printf '%s' "$REPS"    | grep -qE '^[1-9][0-9]*$' || die "--reps must be a positive integer"
printf '%s' "$TIMEOUT" | grep -qE '^[1-9][0-9]*$' || die "--timeout must be a positive integer"
[ -n "$BENCHES" ] || die "--benches must not be empty"
[ -n "$ENGINES" ] || die "--engines must not be empty"

for e in $ENGINES; do
  case "$e" in souffle|flowlog) ;; *) die "unknown engine: $e" ;; esac
done

[ -d "$DOOP_HOME" ]                  || die "no doop home: $DOOP_HOME"
[ -x "$DOOP_HOME/doop" ]             || die "no doop launcher in $DOOP_HOME"
[ -n "$ARTIFACT" ]                   || die "no corpus given: set --artifact or DOOP_BENCH_ARTIFACT"
[ -d "$ARTIFACT/dacapo" ]            || die "no corpus: $ARTIFACT/dacapo"
[ -d "$ARTIFACT/java_platforms/JREs" ] || die "no JREs in $ARTIFACT/java_platforms"
[ -n "$JDK_HOME" ]                   || die "no jdk given: set --jdk-home or JAVA_HOME"
[ -x "$JDK_HOME/bin/java" ]          || die "no jdk: $JDK_HOME"
[ -x "$CARGO_ROOT/bin/cargo" ]       || die "no cargo: $CARGO_ROOT/bin/cargo (set --cargo-root or CARGO_HOME)"

case " $ENGINES " in
  *" flowlog "*)
    [ -n "$FLOWLOG_HOME" ] || die "no flowlog home given: set --flowlog-home or FLOWLOG_HOME"
    [ -d "$FLOWLOG_HOME" ] || die "no flowlog home: $FLOWLOG_HOME"
    [ -x "$FLOWLOG_HOME/flowlog-compiler" ] || die "no executable: $FLOWLOG_HOME/flowlog-compiler" ;;
esac

for b in $BENCHES; do
  [ -d "$ARTIFACT/dacapo/$b/inputs" ] || die "no inputs for bench: $b"
  profile_has "$b" || die "no $PROFILE entry for bench: $b"
  eval "_probe=( $(profile_flags "$b") )" 2>/dev/null || die "$PROFILE flags for $b are not valid shell words: $(profile_flags "$b")"
done

export JAVA_HOME="$JDK_HOME"
export PATH="$JDK_HOME/bin:$CARGO_ROOT/bin:$PATH"
export DOOP_PLATFORMS_LIB="$ARTIFACT/java_platforms"

STAMP="$(date +%Y%m%d-%H%M%S)"
[ -n "$RUNDIR" ] || RUNDIR="$DOOP_HOME/out/engine-bench/$STAMP"
TSV="$RUNDIR/timings.tsv"

mkdir -p "$RUNDIR" || die "cannot create rundir: $RUNDIR"
printf 'bench\tjobs\tengine\trep\tid\texec_sec\tcompile_sec\tpeak_rss_mb\tstatus\toutdir\n' > "$TSV"

{
  echo "stamp=$STAMP"
  echo "analysis=$ANALYSIS platform=$PLATFORM stats=$STATS jobs=$JOBS reps=$REPS timeout=$TIMEOUT database=$DATABASE"
  echo "benches=$BENCHES"
  echo "profile=$PROFILE"
  for b in $BENCHES; do echo "flags[$b]=$(profile_flags "$b")"; done
  echo "engines=$ENGINES"
  echo "doop=$DOOP_HOME"
  echo "doop_rev=$(git -C "$DOOP_HOME" rev-parse --short HEAD 2>/dev/null || echo n/a)"
  echo "doop_dirty=$(git -C "$DOOP_HOME" status --porcelain 2>/dev/null | wc -l)"
  echo "artifact=$ARTIFACT"
  echo "flowlog=$("$FLOWLOG_HOME/flowlog-compiler" --version 2>/dev/null || echo n/a)"
  echo "flowlog_home=$FLOWLOG_HOME"
  echo "java=$("$JDK_HOME/bin/java" -version 2>&1 | head -1)"
  echo "host=$(uname -srm) cores=$(nproc) mem_gb=$(free -g | awk 'NR==2{print $2}')"
  echo "vm.max_map_count=$(sysctl -n vm.max_map_count 2>/dev/null || echo n/a)"
} > "$RUNDIR/env.txt"

find_engine_pid() {
  local p cmd
  for p in /proc/[0-9]*; do
    IFS= read -r -d '' cmd 2>/dev/null < "$p/cmdline" || continue
    case "$cmd" in
      "$DOOP_HOME"/cache/souffle-analyses/*|"$DOOP_HOME"/cache/flowlog-analyses/*)
        printf '%s' "${p#/proc/}"; return 0 ;;
    esac
  done
  return 1
}

read_hwm() {
  local l v
  while IFS= read -r l; do
    case "$l" in
      VmHWM:*) v=${l#VmHWM:}; v=${v%%kB*}; v=${v//[[:space:]]/}; printf '%s' "$v"; return 0 ;;
    esac
  done 2>/dev/null < "/proc/$1/status"
  return 1
}

sample_peak_rss() {
  local out="$1" max=0 pid="" hwm
  while :; do
    if [ -z "$pid" ] || [ ! -d "/proc/$pid" ]; then
      pid=$(find_engine_pid) || pid=""
    fi
    if [ -n "$pid" ]; then
      hwm=$(read_hwm "$pid")
      case "$hwm" in ''|*[!0-9]*) hwm="" ;; esac
      [ -n "$hwm" ] && [ "$hwm" -gt "$max" ] && max=$hwm
      printf '%s' "$max" > "$out.tmp" 2>/dev/null && mv -f "$out.tmp" "$out" 2>/dev/null
      sleep 1
    else
      sleep 2
    fi
  done
}

inputs_of() {
  local b="$1" kind="$2" f
  for f in "$ARTIFACT/dacapo/$b/inputs/$kind"/*.jar; do
    [ -e "$f" ] && printf '%s\n' "$f"
  done
}

run_one() {
  local bench="$1" jobs="$2" engine="$3" rep="$4"
  local id="bench-${ANALYSIS}-${PROFILE}-${bench}-${engine}-s${STATS}-j${jobs}-r${rep}"
  local outdir="$DOOP_HOME/out/$id"
  local rc out exec_sec compile_sec status f rssfile spid peak_rss peak_kb
  local -a cmd extra

  cmd=( ./doop -a "$ANALYSIS" --platform "$PLATFORM" --id "$id"
        --timeout "$TIMEOUT" --cache --stats "$STATS" --souffle-jobs "$jobs" )

  eval "extra=( $(profile_flags "$bench") )"
  [ ${#extra[@]} -gt 0 ] && cmd+=( "${extra[@]}" )

  while IFS= read -r f; do cmd+=( -i "$f" ); done < <(inputs_of "$bench" i)
  while IFS= read -r f; do cmd+=( -l "$f" ); done < <(inputs_of "$bench" l)

  if [ "$engine" = flowlog ]; then
    cmd+=( --Xvia-flowlog "$FLOWLOG_HOME" --Xflowlog-workers "$jobs" )
  fi

  out="$RUNDIR/$id.out"
  rssfile="$RUNDIR/.rss.$$"
  printf '0' > "$rssfile"
  sample_peak_rss "$rssfile" & spid=$!

  ( cd "$DOOP_HOME" && "${cmd[@]}" ) > "$out" 2>&1
  rc=$?

  kill "$spid" 2>/dev/null; wait "$spid" 2>/dev/null
  peak_kb=$(cat "$rssfile" 2>/dev/null)
  case "$peak_kb" in ''|*[!0-9]*) peak_kb=0 ;; esac
  peak_rss=$(( peak_kb / 1024 ))
  rm -f "$rssfile" "$rssfile.tmp"
  [ "$peak_rss" -eq 0 ] && peak_rss=""

  exec_sec=$(sed -e 's/\x1b\[[0-9;]*m//g' "$out" \
    | grep -aE '^analysis execution time \(sec\)' | tail -1 | grep -oE '[0-9,]+$' | tr -d ,)
  compile_sec=$(sed -e 's/\x1b\[[0-9;]*m//g' "$out" \
    | grep -aE '^analysis compilation time \(sec\)' | tail -1 | grep -oE '[0-9,]+$' | tr -d ,)

  if [ $rc -ne 0 ] || grep -aq 'ERROR Main -' "$out"; then
    status=failed
  elif [ -z "$exec_sec" ]; then
    status=no-timing
  else
    status=ok
  fi

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$bench" "$jobs" "$engine" "$rep" "$id" "${exec_sec:-}" "${compile_sec:-}" "${peak_rss:-}" "$status" "$outdir" >> "$TSV"
  local dbnote=""
  if [ "$DATABASE" = drop ] && [ -d "$outdir/database" ]; then
    case "$outdir" in
      "$DOOP_HOME"/out/bench-*) rm -rf -- "$outdir/database" && dbnote=" db=dropped" ;;
      *) dbnote=" db=KEPT(unexpected path)" ;;
    esac
  fi

  printf '  %-9s j%-4s %-8s rep%-3s exec=%-7s peak=%-9s %s%s\n' \
    "$bench" "$jobs" "$engine" "$rep" "${exec_sec:+${exec_sec}s}" "${peak_rss:+${peak_rss}MB}" "$status" "$dbnote"
}

echo "run $STAMP  analysis=$ANALYSIS profile=$PROFILE platform=$PLATFORM stats=$STATS jobs=$JOBS reps=$REPS database=$DATABASE"
echo "results -> $RUNDIR"
echo

for bench in $BENCHES; do
  for jobs in $JOBS; do
    for engine in $ENGINES; do
      for rep in $(seq 1 "$REPS"); do
        run_one "$bench" "$jobs" "$engine" "$rep"
      done
    done
  done
done

echo
awk -F'\t' -v reps="$REPS" -v hdr="$ANALYSIS  profile=$PROFILE  platform=$PLATFORM  stats=$STATS" '
  NR==1 { next }
  { g=$1"\t"$2; if(!seen[g]++) order[++k]=g }
  $9=="ok" { key=$1"\t"$2"\t"$3; n[key]++; v[key,n[key]]=$6; m[key,n[key]]=$8 }
  $9!="ok" { bad[$1"\t"$2"\t"$3]++ }
  END {
    printf "%s  reps=%s\n\n", hdr, reps
    printf "%-9s %6s %9s %9s %8s %9s %9s %9s %s\n","BENCH","JOBS","SOUF_s","FLOW_s","SPEEDUP","SOUF_MB","FLOW_MB","MEM_COST","NOTE"
    for(i=1;i<=k;i++){
      split(order[i], p, "\t"); b=p[1]; j=p[2]
      s=median(b,j,"souffle",v); f=median(b,j,"flowlog",v)
      sm=median(b,j,"souffle",m); fm=median(b,j,"flowlog",m)
      sp = (s!="" && f!="" && f>0) ? sprintf("%.2fx", s/f) : "-"
      mc = (sm!="" && fm!="" && sm>0) ? sprintf("%.2fx", fm/sm) : "-"
      note=""
      if(bad[b"\t"j"\tsouffle"]) note=note sprintf("souffle:%d-bad ", bad[b"\t"j"\tsouffle"])
      if(bad[b"\t"j"\tflowlog"]) note=note sprintf("flowlog:%d-bad ", bad[b"\t"j"\tflowlog"])
      printf "%-9s %6s %9s %9s %8s %9s %9s %9s %s\n", b, j, (s==""?"-":s), (f==""?"-":f), sp, (sm==""?"-":sm), (fm==""?"-":fm), mc, note
    }
  }
  function median(b,j,e,src,  c,x,y,t,arr,key){
    key=b"\t"j"\t"e; c=n[key]; if(c==0) return ""
    for(x=1;x<=c;x++) arr[x]=src[key,x]+0
    for(x=1;x<c;x++) for(y=x+1;y<=c;y++) if(arr[y]<arr[x]){t=arr[x];arr[x]=arr[y];arr[y]=t}
    if(c%2) return arr[(c+1)/2]
    return (arr[c/2]+arr[c/2+1])/2
  }
' "$TSV" | tee "$RUNDIR/summary.txt"

echo
echo "$TSV"

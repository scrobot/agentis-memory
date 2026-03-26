#!/usr/bin/env python3
"""
Agentis Memory Benchmark Report Generator.

Usage:
    python generate_report.py <results_dir> <reports_dir>

Reads memtier_benchmark JSON output from results_dir, generates:
  - reports/report.html    — interactive Plotly report
  - reports/*.png          — static PNG exports
  - Console summary table
"""

import json
import os
import sys
import glob
from pathlib import Path
from datetime import datetime

import pandas as pd
import plotly.graph_objects as go
import plotly.express as px
from plotly.subplots import make_subplots
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from jinja2 import Environment, FileSystemLoader

# ─── Constants ────────────────────────────────────────────────────────────────

SERVERS = ["agentis_memory", "redis", "dragonfly", "lux"]
SERVER_LABELS = {
    "agentis_memory": "Agentis Memory",
    "redis": "Redis 7.4",
    "dragonfly": "Dragonfly",
    "lux": "Lux",
}
SERVER_COLORS = {
    "agentis_memory": "#4C72B0",
    "redis": "#DD4444",
    "dragonfly": "#2E8B57",
    "lux": "#FF8C00",
}

SCENARIOS = ["strings", "hashes", "lists", "sorted-sets", "sets", "mixed-workload"]
SCENARIO_LABELS = {
    "strings": "Strings (SET/GET)",
    "hashes": "Hashes",
    "lists": "Lists",
    "sorted-sets": "Sorted Sets",
    "sets": "Sets",
    "mixed-workload": "Mixed Workload",
}
PIPELINE_DEPTHS = [1, 10, 50, 100]


# ─── Parsing ──────────────────────────────────────────────────────────────────

def parse_memtier_json(path: str) -> dict | None:
    """Parse a memtier_benchmark JSON output file."""
    if not os.path.exists(path):
        return None
    try:
        with open(path) as f:
            data = json.load(f)
    except (json.JSONDecodeError, OSError):
        return None

    # memtier JSON structure: {"ALL STATS": {"Totals": {...}, ...}}
    totals = data.get("ALL STATS", {}).get("Totals", {})
    if not totals:
        # Some versions use a different key
        for key, val in data.get("ALL STATS", {}).items():
            if isinstance(val, dict) and "Ops/sec" in val:
                totals = val
                break

    if not totals:
        return None

    return {
        "ops_sec": totals.get("Ops/sec", 0),
        "hits_sec": totals.get("Hits/sec", 0),
        "misses_sec": totals.get("Misses/sec", 0),
        "avg_latency_ms": totals.get("Latency", 0),
        "p50_ms": totals.get("p50.00", 0),
        "p95_ms": totals.get("p95.00", 0),
        "p99_ms": totals.get("p99.00", 0),
        "p99_9_ms": totals.get("p99.90", 0),
        "kb_sec": totals.get("KB/sec", 0),
    }


def load_all_results(results_dir: str) -> dict:
    """
    Returns nested dict: results[server][scenario] = parsed_stats
    and results[server]["pipeline_N"] = parsed_stats
    """
    results = {s: {} for s in SERVERS}

    for server in SERVERS:
        for scenario in SCENARIOS:
            path = os.path.join(results_dir, server, f"{scenario}.json")
            parsed = parse_memtier_json(path)
            if parsed:
                results[server][scenario] = parsed

        for depth in PIPELINE_DEPTHS:
            path = os.path.join(results_dir, server, f"pipeline_{depth}.json")
            parsed = parse_memtier_json(path)
            if parsed:
                results[server][f"pipeline_{depth}"] = parsed

    return results


# ─── Chart builders ──────────────────────────────────────────────────────────

def build_throughput_chart(results: dict) -> go.Figure:
    """Grouped bar chart: ops/sec per scenario per server."""
    fig = go.Figure()

    for server in SERVERS:
        x_labels = []
        y_values = []
        for scenario in SCENARIOS:
            stats = results[server].get(scenario)
            x_labels.append(SCENARIO_LABELS[scenario])
            y_values.append(stats["ops_sec"] if stats else 0)

        fig.add_trace(go.Bar(
            name=SERVER_LABELS[server],
            x=x_labels,
            y=y_values,
            marker_color=SERVER_COLORS[server],
            text=[f"{v:,.0f}" if v else "N/A" for v in y_values],
            textposition="outside",
        ))

    fig.update_layout(
        title="Throughput by Scenario (ops/sec)",
        xaxis_title="Scenario",
        yaxis_title="Operations / sec",
        barmode="group",
        template="plotly_dark",
        legend=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1),
        height=500,
    )
    return fig


def build_latency_chart(results: dict, metric: str = "p99_ms") -> go.Figure:
    """Grouped bar chart: latency (p50/p95/p99) per scenario per server."""
    metric_labels = {"p50_ms": "p50", "p95_ms": "p95", "p99_ms": "p99"}
    fig = go.Figure()

    for server in SERVERS:
        x_labels = []
        y_values = []
        for scenario in SCENARIOS:
            stats = results[server].get(scenario)
            x_labels.append(SCENARIO_LABELS[scenario])
            y_values.append(stats[metric] if stats else 0)

        fig.add_trace(go.Bar(
            name=SERVER_LABELS[server],
            x=x_labels,
            y=y_values,
            marker_color=SERVER_COLORS[server],
            text=[f"{v:.2f}ms" if v else "N/A" for v in y_values],
            textposition="outside",
        ))

    label = metric_labels.get(metric, metric)
    fig.update_layout(
        title=f"Latency {label} by Scenario",
        xaxis_title="Scenario",
        yaxis_title=f"Latency {label} (ms)",
        barmode="group",
        template="plotly_dark",
        legend=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1),
        height=500,
    )
    return fig


def build_latency_percentiles_chart(results: dict, scenario: str = "strings") -> go.Figure:
    """Line chart: latency percentile profile per server for a given scenario."""
    percentiles = ["p50_ms", "p95_ms", "p99_ms", "p99_9_ms"]
    percentile_labels = ["p50", "p95", "p99", "p99.9"]

    fig = go.Figure()
    for server in SERVERS:
        stats = results[server].get(scenario)
        if not stats:
            continue
        y = [stats.get(p, 0) for p in percentiles]
        fig.add_trace(go.Scatter(
            name=SERVER_LABELS[server],
            x=percentile_labels,
            y=y,
            mode="lines+markers",
            line=dict(color=SERVER_COLORS[server], width=2),
            marker=dict(size=8),
        ))

    fig.update_layout(
        title=f"Latency Percentile Profile — {SCENARIO_LABELS.get(scenario, scenario)}",
        xaxis_title="Percentile",
        yaxis_title="Latency (ms)",
        template="plotly_dark",
        height=400,
    )
    return fig


def build_pipeline_chart(results: dict) -> go.Figure:
    """Line chart: ops/sec vs pipeline depth per server."""
    fig = go.Figure()

    for server in SERVERS:
        y_values = []
        for depth in PIPELINE_DEPTHS:
            stats = results[server].get(f"pipeline_{depth}")
            y_values.append(stats["ops_sec"] if stats else None)

        fig.add_trace(go.Scatter(
            name=SERVER_LABELS[server],
            x=PIPELINE_DEPTHS,
            y=y_values,
            mode="lines+markers",
            line=dict(color=SERVER_COLORS[server], width=2),
            marker=dict(size=10),
        ))

    fig.update_layout(
        title="Pipeline Scaling (ops/sec vs pipeline depth)",
        xaxis_title="Pipeline Depth",
        yaxis_title="Operations / sec",
        xaxis=dict(type="log", tickvals=PIPELINE_DEPTHS, ticktext=[str(d) for d in PIPELINE_DEPTHS]),
        template="plotly_dark",
        height=450,
    )
    return fig


def build_cdf_chart(results: dict, scenario: str = "strings") -> go.Figure:
    """
    Approximate CDF from available percentile points.
    Real CDF would need HDR histogram data; this uses the 4 percentile points as proxy.
    """
    percentiles_pct = [50, 95, 99, 99.9]
    percentile_keys = ["p50_ms", "p95_ms", "p99_ms", "p99_9_ms"]

    fig = go.Figure()
    for server in SERVERS:
        stats = results[server].get(scenario)
        if not stats:
            continue
        x = [stats.get(k, 0) for k in percentile_keys]
        fig.add_trace(go.Scatter(
            name=SERVER_LABELS[server],
            x=x,
            y=percentiles_pct,
            mode="lines+markers",
            line=dict(color=SERVER_COLORS[server], width=2),
            marker=dict(size=8),
        ))

    fig.update_layout(
        title=f"Latency CDF (approx.) — {SCENARIO_LABELS.get(scenario, scenario)}",
        xaxis_title="Latency (ms)",
        yaxis_title="Percentile",
        template="plotly_dark",
        height=400,
    )
    return fig


def build_heatmap(results: dict) -> go.Figure:
    """
    Heatmap: rows=scenarios, cols=servers, value=ratio vs Redis throughput.
    Green > 1x (faster than Redis), Red < 1x (slower).
    """
    z = []
    text = []
    y_labels = [SCENARIO_LABELS[s] for s in SCENARIOS]
    x_labels = [SERVER_LABELS[s] for s in SERVERS]

    for scenario in SCENARIOS:
        redis_ops = (results["redis"].get(scenario) or {}).get("ops_sec", 0)
        row_z = []
        row_text = []
        for server in SERVERS:
            stats = results[server].get(scenario)
            ops = stats["ops_sec"] if stats else 0
            if redis_ops and ops:
                ratio = ops / redis_ops
                row_z.append(ratio)
                row_text.append(f"{ratio:.2f}x")
            else:
                row_z.append(None)
                row_text.append("N/A")
        z.append(row_z)
        text.append(row_text)

    fig = go.Figure(data=go.Heatmap(
        z=z,
        x=x_labels,
        y=y_labels,
        text=text,
        texttemplate="%{text}",
        colorscale=[
            [0.0, "#8B0000"],
            [0.5, "#888888"],
            [1.0, "#006400"],
        ],
        zmid=1.0,
        colorbar=dict(title="vs Redis"),
        hoverongaps=False,
    ))

    fig.update_layout(
        title="Throughput Ratio vs Redis (green = faster, red = slower)",
        template="plotly_dark",
        height=400,
    )
    return fig


# ─── Static PNG exports (matplotlib) ─────────────────────────────────────────

def save_throughput_png(results: dict, out_path: str):
    scenarios_short = [SCENARIO_LABELS[s].split(" ")[0] for s in SCENARIOS]
    x = range(len(SCENARIOS))
    width = 0.2

    fig, ax = plt.subplots(figsize=(14, 6), facecolor="#1a1a2e")
    ax.set_facecolor("#16213e")

    for i, server in enumerate(SERVERS):
        vals = [(results[server].get(s) or {}).get("ops_sec", 0) for s in SCENARIOS]
        offset = (i - 1.5) * width
        bars = ax.bar([xi + offset for xi in x], vals, width, label=SERVER_LABELS[server],
                      color=SERVER_COLORS[server], alpha=0.9)
        for bar, v in zip(bars, vals):
            if v:
                ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + max(vals) * 0.01,
                        f"{v/1000:.0f}k", ha="center", va="bottom", fontsize=7, color="white")

    ax.set_xticks(list(x))
    ax.set_xticklabels(scenarios_short, color="white")
    ax.set_ylabel("Operations / sec", color="white")
    ax.set_title("Throughput by Scenario", color="white", fontsize=14)
    ax.tick_params(colors="white")
    ax.spines["bottom"].set_color("#444")
    ax.spines["left"].set_color("#444")
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.legend(facecolor="#1a1a2e", labelcolor="white")
    ax.yaxis.set_tick_params(color="white")

    plt.tight_layout()
    plt.savefig(out_path, dpi=150, bbox_inches="tight", facecolor=fig.get_facecolor())
    plt.close()


def save_latency_p99_png(results: dict, out_path: str):
    scenarios_short = [SCENARIO_LABELS[s].split(" ")[0] for s in SCENARIOS]
    x = range(len(SCENARIOS))
    width = 0.2

    fig, ax = plt.subplots(figsize=(14, 6), facecolor="#1a1a2e")
    ax.set_facecolor("#16213e")

    for i, server in enumerate(SERVERS):
        vals = [(results[server].get(s) or {}).get("p99_ms", 0) for s in SCENARIOS]
        offset = (i - 1.5) * width
        ax.bar([xi + offset for xi in x], vals, width, label=SERVER_LABELS[server],
               color=SERVER_COLORS[server], alpha=0.9)

    ax.set_xticks(list(x))
    ax.set_xticklabels(scenarios_short, color="white")
    ax.set_ylabel("Latency p99 (ms)", color="white")
    ax.set_title("p99 Latency by Scenario", color="white", fontsize=14)
    ax.tick_params(colors="white")
    ax.spines["bottom"].set_color("#444")
    ax.spines["left"].set_color("#444")
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.legend(facecolor="#1a1a2e", labelcolor="white")

    plt.tight_layout()
    plt.savefig(out_path, dpi=150, bbox_inches="tight", facecolor=fig.get_facecolor())
    plt.close()


def save_pipeline_png(results: dict, out_path: str):
    fig, ax = plt.subplots(figsize=(10, 6), facecolor="#1a1a2e")
    ax.set_facecolor("#16213e")

    for server in SERVERS:
        vals = [(results[server].get(f"pipeline_{d}") or {}).get("ops_sec", None)
                for d in PIPELINE_DEPTHS]
        ax.plot(PIPELINE_DEPTHS, vals, "o-", label=SERVER_LABELS[server],
                color=SERVER_COLORS[server], linewidth=2, markersize=8)

    ax.set_xscale("log")
    ax.set_xticks(PIPELINE_DEPTHS)
    ax.set_xticklabels([str(d) for d in PIPELINE_DEPTHS], color="white")
    ax.set_ylabel("Operations / sec", color="white")
    ax.set_xlabel("Pipeline Depth", color="white")
    ax.set_title("Pipeline Scaling", color="white", fontsize=14)
    ax.tick_params(colors="white")
    ax.spines["bottom"].set_color("#444")
    ax.spines["left"].set_color("#444")
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.legend(facecolor="#1a1a2e", labelcolor="white")

    plt.tight_layout()
    plt.savefig(out_path, dpi=150, bbox_inches="tight", facecolor=fig.get_facecolor())
    plt.close()


# ─── Console summary ──────────────────────────────────────────────────────────

def print_summary(results: dict):
    print("\n" + "=" * 70)
    print("  BENCHMARK SUMMARY — ops/sec (strings scenario)")
    print("=" * 70)

    header = f"{'Server':<20} {'ops/sec':>12} {'p50 ms':>8} {'p99 ms':>8} {'vs Redis':>10}"
    print(header)
    print("-" * 70)

    redis_ops = (results["redis"].get("strings") or {}).get("ops_sec", 0)

    for server in SERVERS:
        stats = results[server].get("strings")
        if not stats:
            print(f"  {SERVER_LABELS[server]:<18} {'N/A':>12}")
            continue
        ops = stats["ops_sec"]
        p50 = stats["p50_ms"]
        p99 = stats["p99_ms"]
        ratio = f"{ops/redis_ops:.2f}x" if redis_ops else "N/A"
        print(f"  {SERVER_LABELS[server]:<18} {ops:>12,.0f} {p50:>8.3f} {p99:>8.3f} {ratio:>10}")

    print("=" * 70 + "\n")


# ─── HTML report ──────────────────────────────────────────────────────────────

def generate_html_report(results: dict, reports_dir: str):
    template_dir = os.path.join(os.path.dirname(__file__), "templates")
    env = Environment(loader=FileSystemLoader(template_dir), autoescape=False)
    template = env.get_template("report.html.j2")

    # Build all charts as HTML div snippets
    charts = {}

    charts["throughput"] = build_throughput_chart(results).to_html(
        full_html=False, include_plotlyjs=False)
    charts["latency_p99"] = build_latency_chart(results, "p99_ms").to_html(
        full_html=False, include_plotlyjs=False)
    charts["latency_p95"] = build_latency_chart(results, "p95_ms").to_html(
        full_html=False, include_plotlyjs=False)
    charts["pipeline"] = build_pipeline_chart(results).to_html(
        full_html=False, include_plotlyjs=False)
    charts["heatmap"] = build_heatmap(results).to_html(
        full_html=False, include_plotlyjs=False)

    # Per-scenario CDF charts
    charts["cdf"] = {}
    charts["percentile_profile"] = {}
    for scenario in SCENARIOS:
        charts["cdf"][scenario] = build_cdf_chart(results, scenario).to_html(
            full_html=False, include_plotlyjs=False)
        charts["percentile_profile"][scenario] = build_latency_percentiles_chart(
            results, scenario).to_html(full_html=False, include_plotlyjs=False)

    # Build summary table data
    summary_rows = []
    redis_ops_by_scenario = {s: (results["redis"].get(s) or {}).get("ops_sec", 0)
                              for s in SCENARIOS}
    for scenario in SCENARIOS:
        row = {"scenario": SCENARIO_LABELS[scenario], "servers": {}}
        for server in SERVERS:
            stats = results[server].get(scenario)
            redis_ops = redis_ops_by_scenario[scenario]
            if stats:
                ratio = stats["ops_sec"] / redis_ops if redis_ops else None
                row["servers"][SERVER_LABELS[server]] = {
                    "ops_sec": f"{stats['ops_sec']:,.0f}",
                    "p50": f"{stats['p50_ms']:.3f}",
                    "p99": f"{stats['p99_ms']:.3f}",
                    "ratio": f"{ratio:.2f}x" if ratio else "N/A",
                    "ratio_val": ratio or 0,
                }
            else:
                row["servers"][SERVER_LABELS[server]] = {
                    "ops_sec": "N/A", "p50": "N/A", "p99": "N/A",
                    "ratio": "N/A", "ratio_val": 0,
                }
        summary_rows.append(row)

    html = template.render(
        generated_at=datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        charts=charts,
        scenarios=SCENARIOS,
        scenario_labels=SCENARIO_LABELS,
        servers=SERVERS,
        server_labels=SERVER_LABELS,
        summary_rows=summary_rows,
        pipeline_depths=PIPELINE_DEPTHS,
    )

    out_path = os.path.join(reports_dir, "report.html")
    with open(out_path, "w") as f:
        f.write(html)
    print(f"  HTML report: {out_path}")


# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    if len(sys.argv) < 3:
        print(f"Usage: {sys.argv[0]} <results_dir> <reports_dir>")
        sys.exit(1)

    results_dir = sys.argv[1]
    reports_dir = sys.argv[2]
    os.makedirs(reports_dir, exist_ok=True)

    print(f"Loading results from: {results_dir}")
    results = load_all_results(results_dir)

    # Count available data points
    total = sum(len(v) for v in results.values())
    print(f"  Loaded {total} result files across {len(SERVERS)} servers.")

    if total == 0:
        print("  WARNING: No result files found. Run run.sh first.")

    print_summary(results)

    print("Generating PNG charts...")
    save_throughput_png(results, os.path.join(reports_dir, "throughput.png"))
    save_latency_p99_png(results, os.path.join(reports_dir, "latency_p99.png"))
    save_pipeline_png(results, os.path.join(reports_dir, "pipeline_scaling.png"))
    print("  throughput.png, latency_p99.png, pipeline_scaling.png")

    print("Generating HTML report...")
    generate_html_report(results, reports_dir)

    print("Done.")


if __name__ == "__main__":
    main()

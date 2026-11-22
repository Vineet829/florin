const plugin = require("tailwindcss/plugin");

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  corePlugins: { preflight: false },
  important: "#root",
  theme: {
    extend: {
      fontFamily: {
        display: ["Sora", "sans-serif"],
        ui: [
          "Geist",
          "IBM Plex Sans",
          "-apple-system",
          "BlinkMacSystemFont",
          "sans-serif",
        ],
        mono: ["IBM Plex Mono", "monospace"],
      },
      colors: {
        spot: {
          ink: "#232735",
          text: "#3d4152",
          body: "#4f5566",
          nav: "#4a4659",
          sub: "#585c6b",
          muted: "#5d6170",
          faint: "#6b7080",
          disabled: "#a3a3b3",
          night: "#30234f",
          violet: "#5b47a0",
          brand: "#7c62d6",
          lilac: "#a996e0",
          coral: "#e8835f",
          blue: "#3f79b0",
          canvas: "#e6e5f0",
          drawer: "#f0eef8",
          thead: "#f3f2f8",
        },
        card: {
          blue: {
            DEFAULT: "#cde3f4",
            ink: "#1f2a3a",
            muted: "#3f4d62",
            decimal: "#5a6a80",
            label: "#2b3a4d",
            icon: "#2e5b86",
            bar: "#3f79b0",
          },
          lilac: {
            DEFAULT: "#e5e1f8",
            ink: "#261f3d",
            muted: "#4a4262",
            decimal: "#665d87",
            label: "#33294f",
            icon: "#5b47a0",
            bar: "#5b47a0",
          },
          peach: {
            DEFAULT: "#f9d9ca",
            ink: "#3a2218",
            muted: "#5e3a2c",
            decimal: "#8a5d4d",
            label: "#4d2d20",
            icon: "#a4502f",
            bar: "#c9643f",
          },
          sage: {
            DEFAULT: "#dcebd6",
            ink: "#1f3320",
            muted: "#3f5a3a",
            decimal: "#4f6a4a",
            label: "#233621",
            icon: "#2f5a2a",
            bar: "#4f8a4a",
            loss: "#8c3b1f",
          },
        },
        change: {
          up: "#2f6b2c",
          down: "#a4502f",
        },
        owed: {
          due: "#9f91dc",
          overdue: "#b5532f",
          age: {
            1: "#e8835f",
            2: "#c9643f",
            3: "#a4502f",
            4: "#8c3b1f",
          },
        },
        flow: {
          out: "#d9774f",
          net: "#2d3142",
        },
        avatar: {
          lilac: "#e5e1f8",
          "lilac-ink": "#4a3a80",
          blue: "#d5e6f5",
          "blue-ink": "#2f5e86",
          peach: "#f9dccf",
          "peach-ink": "#8a4a2f",
          sage: "#e1ecdc",
          "sage-ink": "#40603a",
        },
        overdue: {
          DEFAULT: "#fbe3d8",
          ink: "#5a2616",
          muted: "#7a4a38",
          icon: "#b0502e",
          strong: "#9c4526",
        },
      },
      backgroundImage: {
        canvas:
          "radial-gradient(ellipse at 8% 0%, #eef0fb 0, #eef0fb00 45%), radial-gradient(ellipse at 95% 12%, #d6e6f3 0, #d6e6f300 42%), radial-gradient(ellipse at 72% 100%, #f1dcd2 0, #f1dcd200 45%), radial-gradient(ellipse at 20% 95%, #ddd3ee 0, #ddd3ee00 50%)",
      },
      boxShadow: {
        panel: "0 30px 60px -40px #463e7873",
        "panel-soft": "0 30px 60px -40px #463e7866",
        nav: "0 10px 24px -14px #3c346473, inset 0 0 0 1px #ffffffe6",
        float: "0 18px 40px -20px #3c346473",
        key: "0 6px 14px -8px #3c346480",
        focus: "0 0 0 3px #5b47a029",
        invalid: "0 0 0 3px #b5532f24",
        menu: "0 24px 48px -28px #28225080, 0 2px 6px -2px #2822501f",
        sheet: "0 -20px 40px -24px #28225080",
      },
      keyframes: {
        "ov-rise": { from: { opacity: "0", transform: "translateY(10px)" } },
        "ov-bar": { from: { transform: "scaleX(0)" } },
        "ov-column": { from: { transform: "scaleY(0)" } },
        "ov-roll": {
          from: { transform: "translateY(400%)" },
          to: { transform: "translateY(0)" },
        },
        "ov-pulse": { to: { opacity: "0.4" } },
        "ov-fade": { from: { opacity: "0" } },
        "ov-lift": { from: { opacity: "0", translate: "0 var(--lift, 4px)" } },
        "auth-rise": {
          from: { opacity: "0", translate: "0 var(--rise, 12px)" },
        },
        "auth-sheen": { to: { translate: "100% 0" } },
        "auth-divider": {
          from: { backgroundPosition: "200% 0" },
          to: { backgroundPosition: "-200% 0" },
        },
        "auth-glow-1": {
          "0%, 100%": { transform: "translate(0, 0) scale(1)", opacity: "0.6" },
          "50%": {
            transform: "translate(20px, 15px) scale(1.15)",
            opacity: "1",
          },
        },
        "auth-glow-2": {
          "0%, 100%": { transform: "translate(0, 0) scale(1)", opacity: "0.4" },
          "50%": {
            transform: "translate(-15px, -20px) scale(1.1)",
            opacity: "0.8",
          },
        },
        "auth-glow-3": {
          "0%, 100%": { transform: "translate(0, 0) scale(1)", opacity: "0.3" },
          "50%": {
            transform: "translate(10px, -25px) scale(1.2)",
            opacity: "0.6",
          },
        },
        "auth-grid": { to: { backgroundPosition: "60px 60px" } },
        "auth-streak": {
          "0%": { left: "-20%", opacity: "0" },
          "20%, 80%": { opacity: "1" },
          "100%": { left: "110%", opacity: "0" },
        },
        "auth-float": {
          "0%, 100%": { transform: "translate(0, 0)", opacity: "0.15" },
          "25%": { opacity: "0.5" },
          "50%": {
            transform: "translate(var(--dx), var(--dy))",
            opacity: "0.3",
          },
          "75%": { opacity: "0.6" },
        },
        "auth-grow": { from: { width: "0" } },
        "auth-wave": {
          "0%": { translate: "-100% 0" },
          "55%, 100%": { translate: "100% 0" },
        },
        "auth-dot": {
          "0%": { left: "0", opacity: "0" },
          "5%, 95%": { opacity: "1" },
          "100%": { left: "calc(100% - 12px)", opacity: "0" },
        },
        "auth-row": { from: { opacity: "0", translate: "-8px 0" } },
        "auth-toast": {
          "0%": { opacity: "0", translate: "0 8px" },
          "12%, 85%": { opacity: "1", translate: "0 0" },
          "100%": { opacity: "0", translate: "0 -4px" },
        },
        "home-lift": { from: { transform: "translateY(16px)" } },
        "home-pop": {
          from: { opacity: "0", transform: "translateY(-8px) scale(0.96)" },
        },
        "home-ripple": {
          from: {
            opacity: "0.9",
            transform: "translate(-50%, -50%) scale(0.3)",
          },
          to: { opacity: "0", transform: "translate(-50%, -50%) scale(1.9)" },
        },
        "home-float": {
          "0%, 100%": { transform: "translateY(0)" },
          "50%": { transform: "translateY(-10px)" },
        },
        "home-stamp": {
          from: {
            opacity: "0",
            transform: "translate(-50%, -50%) rotate(-12deg) scale(1.6)",
          },
          to: {
            opacity: "1",
            transform: "translate(-50%, -50%) rotate(-12deg) scale(1)",
          },
        },
        "home-progress": { from: { transform: "scaleX(0)" } },
      },
      animation: {
        "ov-rise":
          "ov-rise 0.7s var(--rise-delay, 0ms) cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "ov-bar": "ov-bar 1.1s 0.5s cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "ov-column":
          "ov-column 0.9s var(--column-delay, 300ms) cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "ov-roll":
          "ov-roll 1.4s calc(300ms + var(--digit-delay, 0ms)) cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "ov-pulse": "ov-pulse 1.6s ease-in-out infinite alternate",
        "ov-fade": "ov-fade 0.12s ease-out both",
        "ov-lift": "ov-lift 0.12s ease-out both",
        "auth-rise":
          "auth-rise 0.6s cubic-bezier(0.16, 1, 0.3, 1) var(--d, 0s) backwards",
        "auth-sheen": "auth-sheen 0.7s ease forwards",
        "auth-divider": "auth-divider 4s linear infinite",
        "auth-glow-1": "auth-glow-1 8s ease-in-out infinite",
        "auth-glow-2": "auth-glow-2 10s ease-in-out infinite",
        "auth-glow-3": "auth-glow-3 12s ease-in-out infinite",
        "auth-grid": "auth-grid 25s linear infinite",
        "auth-streak": "auth-streak 6s ease-in-out infinite",
        "auth-streak-late": "auth-streak 6s ease-in-out -3s infinite",
        "auth-float": "auth-float var(--dur) ease-in-out infinite",
        "auth-fill":
          "auth-grow 1.8s cubic-bezier(0.4, 0, 0.2, 1) 0.8s backwards",
        "auth-dot": "auth-dot 1.8s cubic-bezier(0.4, 0, 0.2, 1) 0.8s",
        "auth-loader-wave": "auth-wave 1.8s ease-in-out 2.6s infinite",
        "auth-row": "auth-row 0.45s ease var(--rd, 1.5s) backwards",
        "auth-grow":
          "auth-grow 0.7s cubic-bezier(0.16, 1, 0.3, 1) var(--gd, 1.4s) backwards",
        "auth-shimmer":
          "auth-wave 2.2s ease-in-out calc(1.9s + var(--sd, 0s)) infinite",
        "auth-fade": "ov-fade 0.6s ease 1.8s backwards",
        "auth-toast": "auth-toast 2.6s ease 3.2s",
        "home-lift":
          "home-lift 0.9s var(--lift-delay, 0ms) cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "home-pop":
          "home-pop 0.45s var(--pop-delay, 0ms) cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "home-ripple": "home-ripple 0.8s ease-out both",
        "home-float": "home-float 7s ease-in-out infinite",
        "home-stamp": "home-stamp 0.5s cubic-bezier(0.3, 1.5, 0.5, 1) both",
        "home-progress": "home-progress 2.8s linear both",
      },
    },
  },
  plugins: [
    plugin(({ addVariant }) => {
      addVariant(
        "compact",
        "@media (min-width: 1360px) and (max-height: 900px)",
      );
      addVariant("coarse", "@media (pointer: coarse)");
      addVariant("stacked", [
        "@container table (max-width: 860px)",
        "@container wide-table (max-width: 960px)",
        "@container broad-table (max-width: 1200px)",
        "@container narrow-table (max-width: 400px)",
      ]);
      addVariant("tabled", [
        "@container table (width > 860px)",
        "@container wide-table (width > 960px)",
        "@container broad-table (width > 1200px)",
        "@container narrow-table (width > 400px)",
      ]);
      addVariant("items-md", "@container items (max-width: 900px)");
      addVariant("items-sm", "@container items (max-width: 480px)");
    }),
  ],
};

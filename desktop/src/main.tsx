import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./app/App";
import { QuickCapture } from "./capture/QuickCapture";
import "./index.css";

const isCapture = new URLSearchParams(window.location.search).get("window") === "capture";

createRoot(document.getElementById("root")!).render(<StrictMode>{isCapture ? <QuickCapture /> : <App />}</StrictMode>);

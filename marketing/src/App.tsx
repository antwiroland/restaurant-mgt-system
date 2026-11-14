import { MotionConfig } from "framer-motion";
import { LightboxProvider } from "./components/Lightbox";
import Home from "./pages/Home";

export default function App() {
  return (
    <MotionConfig reducedMotion="user">
      <LightboxProvider>
        <Home />
      </LightboxProvider>
    </MotionConfig>
  );
}

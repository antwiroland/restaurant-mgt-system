import CTASection from "../components/CTASection";
import Contact from "../components/Contact";
import DemoRequest from "../components/DemoRequest";
import FoodGallery from "../components/FoodGallery";
import Footer from "../components/Footer";
import Hero from "../components/Hero";
import Navbar from "../components/Navbar";
import ScreenshotShowcase from "../components/ScreenshotShowcase";
import AboutProduct from "../sections/AboutProduct";
import AttendanceSection from "../sections/AttendanceSection";
import CustomerOrdering from "../sections/CustomerOrdering";
import PaymentSection from "../sections/PaymentSection";
import PayrollSection from "../sections/PayrollSection";
import POSSection from "../sections/POSSection";
import ReportingSection from "../sections/ReportingSection";
import StaffManagement from "../sections/StaffManagement";
import TableManagement from "../sections/TableManagement";
import ValueProps from "../sections/ValueProps";
import WhoWeServe from "../sections/WhoWeServe";
import WhyChoose from "../sections/WhyChoose";

export default function Home() {
  return (
    <>
      <a href="#main" className="sr-only z-[200] rounded-lg bg-ink px-4 py-2 text-cream focus:not-sr-only focus:fixed focus:top-3 focus:left-3">
        Skip to content
      </a>
      <Navbar />
      <main id="main">
        <Hero />
        <FoodGallery />
        <ValueProps />
        <AboutProduct />
        <ScreenshotShowcase />
        <CustomerOrdering />
        <TableManagement />
        <POSSection />
        <PaymentSection />
        <StaffManagement />
        <AttendanceSection />
        <PayrollSection />
        <ReportingSection />
        <WhoWeServe />
        <WhyChoose />
        <CTASection />
        <DemoRequest />
        <Contact />
      </main>
      <Footer />
    </>
  );
}

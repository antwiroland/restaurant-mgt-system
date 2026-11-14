// Company and contact details supplied by KENVO Integrated Systems. Add nothing here that they haven't confirmed.
export const company = {
  name: "KENVO Integrated Systems",
  short: "KENVO",
  website: "https://kenvointegratedsystems.com",
  email: "info@kenvointegratedsystems.com",
  directEmail: "rolandantwisenior47@gmail.com",
  phones: [
    { display: "024 107 8835", intl: "+233241078835", wa: "233241078835" },
    { display: "053 524 6291", intl: "+233535246291", wa: "233535246291" },
  ],
  acronym: [
    ["K", "Knowledge"],
    ["E", "Electrical"],
    ["N", "Networking"],
    ["V", "Vision"],
    ["O", "Operations"],
  ] as const,
  corporateTagline: "CONNECT. SECURE. POWER.",
};

export const product = {
  name: "Restaurant Management System",
  tagline: "CONNECT. MANAGE. SERVE. GROW.",
  headline: "Smarter Restaurant Management, Powered by KENVO.",
  statement:
    "An integrated restaurant management solution developed by KENVO Integrated Systems to help hospitality businesses streamline operations, enhance customer experiences, improve accountability, and manage their teams more effectively.",
  closing: "Built by KENVO Integrated Systems. Designed to make restaurant management smarter.",
};

export const nav = [
  { label: "Home", href: "#home" },
  { label: "Features", href: "#features" },
  { label: "Solutions", href: "#solutions" },
  { label: "How It Works", href: "#how-it-works" },
  { label: "Contact", href: "#contact" },
];

export const whatsapp = (wa: string, text = "Hello KENVO, I'd like to know more about the Restaurant Management System.") =>
  `https://wa.me/${wa}?text=${encodeURIComponent(text)}`;

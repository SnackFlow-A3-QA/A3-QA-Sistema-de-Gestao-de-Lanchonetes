/* Tema do Tailwind (CDN) com as cores do Design System. Carregado logo após o script do CDN. */
tailwind.config = {
  theme: {
    extend: {
      fontFamily: { sans: ['Inter', 'system-ui', 'sans-serif'] },
      colors: {
        brand: {
          orange: '#EA580C',
          orangeHover: '#C2410C',
          dark: '#172B4D',
          blue: '#2563EB',
          bg: '#F8FAFC',
          text: '#1E293B',
          muted: '#64748B',
          border: '#E2E8F0'
        }
      }
    }
  }
};

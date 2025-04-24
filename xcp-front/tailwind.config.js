module.exports = {
    content: [
        './index.html',
        './src/**/*.{vue,js,ts}'
    ],
    theme: {
        extend: {}
    },
    plugins: [
        // DaisyUI (기존)
        require('daisyui'),

        // .card를 유틸로 등록 → @apply card; 가능
        function ({ addUtilities }) {
            const newUtils = {
                '.card': {
                    '@apply bg-white rounded-lg shadow-md p-6': {}
                }
            };
            addUtilities(newUtils, ['responsive']);
        }
    ],
    daisyui: {
        themes: ['light', 'dark']
    }
}
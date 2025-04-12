import { ref, watch } from 'vue'
import axios from 'axios'

export function useExchangeCalculator(baseCurrency, targetCurrency, amount) {
    const exchangeRate = ref(null)
    const convertedAmount = ref(null)

    watch([baseCurrency, targetCurrency, amount], async () => {
        if (!baseCurrency.value || !targetCurrency.value || !amount.value) {
            convertedAmount.value = null
            return
        }
        try {
            const res = await axios.get(`http://localhost:8080/api/exchange-rate/rate?baseCurrency=${baseCurrency.value}&targetCurrency=${targetCurrency.value}`)
            exchangeRate.value = res.data.conversion_rates[targetCurrency.value]
            convertedAmount.value = (amount.value * exchangeRate.value).toFixed(2)
        } catch (e) {
            console.error('환율 계산 실패:', e)
            convertedAmount.value = null
        }
    })

    return {
        exchangeRate,
        convertedAmount
    }
}

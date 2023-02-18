enum Tax {
  /// Без НДС
  NONE,

  ///  НДС по ставке 0%
  VAT_0,

  /// НДС чека по ставке 10%
  VAT_10,

  ///НДС чека по ставке 18%
  VAT_18,

  ///  НДС чека по расчетной ставке 10/110
  VAT_110,

  /// НДС чека по расчетной ставке 18/118
  VAT_118,

  /// НДС чека по ставке 20%
  VAT_20,

  /// НДС чека по расчетной ставке 20/120
  VAT_120
}

extension TaxIos on Tax {
  String toIos() {
    switch (this) {
      case Tax.NONE:
        return "none";
      case Tax.VAT_0:
        return "vat0";
      case Tax.VAT_10:
        return "vat10";
      case Tax.VAT_18:
        return "vat18";
      case Tax.VAT_110:
        return "vat110";
      case Tax.VAT_118:
        return "vat118";
      case Tax.VAT_20:
        return "vat20";
      case Tax.VAT_120:
        return "vat120";
    }
  }
}

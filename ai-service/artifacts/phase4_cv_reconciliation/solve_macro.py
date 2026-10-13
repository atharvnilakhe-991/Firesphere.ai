# Solve for Message 1 Macro F1 formula
# Given for Message 1:
# tau=0.05: Macro F1=0.2078, FE=(P=0.264, R=0.741, F1=0.389), ES=(P=0.274, R=0.337, F1=0.302)
# tau=0.08: Macro F1=0.2227, FE=(P=0.347, R=0.643, F1=0.451), ES=(P=0.316, R=0.175, F1=0.225)
# tau=0.10: Macro F1=0.2258, FE=(P=0.392, R=0.598, F1=0.473), ES=(P=0.366, R=0.106, F1=0.165)
# tau=0.12: Macro F1=0.2234, FE=(P=0.424, R=0.560, F1=0.482), ES=(P=0.354, R=0.058, F1=0.100)
# tau=0.15: Macro F1=0.2045, FE=(P=0.465, R=0.505, F1=0.484), ES=(P=0.267, R=0.016, F1=0.030)
# tau=0.18: Macro F1=0.1989, FE=(P=0.504, R=0.468, F1=0.486), ES=(P=0.273, R=0.006, F1=0.012)
# tau=0.20: Macro F1=0.1210, FE=(P=0.524, R=0.443, F1=0.480), ES=(P=0.143, R=0.002, F1=0.004)
# tau=0.25: Macro F1=0.1171, FE=(P=0.563, R=0.392, F1=0.463), ES=(P=0.000, R=0.000, F1=0.000)
# tau=0.30: Macro F1=0.1106, FE=(P=0.613, R=0.331, F1=0.430), ES=(P=0.000, R=0.000, F1=0.000)

data = [
    (0.05, 0.2078, 0.389, 0.302),
    (0.08, 0.2227, 0.451, 0.225),
    (0.10, 0.2258, 0.473, 0.165),
    (0.12, 0.2234, 0.482, 0.100),
    (0.15, 0.2045, 0.484, 0.030),
    (0.18, 0.1989, 0.486, 0.012),
    (0.20, 0.1210, 0.480, 0.004),
    (0.25, 0.1171, 0.463, 0.000),
    (0.30, 0.1106, 0.430, 0.000),
]

for tau, target_macro, fe_f1, es_f1 in data:
    sum_f1 = fe_f1 + es_f1
    # If divided by 4:
    div4 = sum_f1 / 4.0
    # If divided by 2:
    div2 = sum_f1 / 2.0
    # What would other classes need to sum to in order to get target_macro * 4?
    # target_macro = (fe_f1 + es_f1 + other) / 4 => other = target_macro * 4 - sum_f1
    other = target_macro * 4.0 - sum_f1
    # What if divided by 3?
    div3 = sum_f1 / 3.0
    print(f"tau={tau:0.2f}: target={target_macro:0.4f} | sum={sum_f1:0.3f} | div4={div4:0.4f} | div2={div2:0.4f} | other={other:0.4f}")

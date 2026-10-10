package com.myshop.modules.product.serviceimpl;

import com.myshop.modules.product.entity.ProductBrand;
import com.myshop.modules.product.mapper.ProductBrandMapper;
import com.myshop.modules.product.service.ProductBrandService;
import com.myshop.modules.product.vo.ProductBrandVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductBrandServiceImpl implements ProductBrandService {

    private final ProductBrandMapper productBrandMapper;

    public ProductBrandServiceImpl(ProductBrandMapper productBrandMapper) {
        this.productBrandMapper = productBrandMapper;
    }

    @Override
    public ProductBrand getById(String id) {
        return productBrandMapper.selectById(id);
    }

    @Override
    public List<ProductBrand> getAll() {
        return productBrandMapper.selectList(null);
    }

    @Override
    public boolean create(ProductBrandVO vo) {

        ProductBrand brand = new ProductBrand();

        brand.setName(vo.getName());
        brand.setLogo(vo.getLogo());

        if (vo.getDisabled() == null) {
            brand.setDisabled(false);
        } else {
            brand.setDisabled(vo.getDisabled());
        }

        brand.setDeleteFlag(0);

        LocalDateTime now = LocalDateTime.now();
        brand.setCreateTime(now);
        brand.setUpdateTime(now);

        return productBrandMapper.insert(brand) > 0;
    }

    @Override
    public boolean update(String id, ProductBrandVO vo) {

        ProductBrand brand = productBrandMapper.selectById(id);

        if (brand == null) {
            return false;
        }

        brand.setName(vo.getName());
        brand.setLogo(vo.getLogo());

        if (vo.getDisabled() != null) {
            brand.setDisabled(vo.getDisabled());
        }

        brand.setUpdateTime(LocalDateTime.now());

        return productBrandMapper.updateById(brand) > 0;
    }

    @Override
    public boolean disable(String id, boolean disable) {

        ProductBrand brand = productBrandMapper.selectById(id);

        if (brand == null) {
            return false;
        }

        brand.setDisabled(disable);
        brand.setUpdateTime(LocalDateTime.now());

        return productBrandMapper.updateById(brand) > 0;
    }

    @Override
    public boolean deleteByIds(List<String> ids) {

        return productBrandMapper.deleteBatchIds(ids) > 0;
    }
}